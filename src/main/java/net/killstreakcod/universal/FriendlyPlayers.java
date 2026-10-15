package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * UUID relationships belong to the save, not to the client or installation.
 * 基于 UUID 的队友关系属于各自存档，不属于客户端或安装实例。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class FriendlyPlayers {
    private static final Map<MinecraftServer, State> WORLDS = new WeakHashMap<>();
    private static final int MAX_TEAMMATES = 4096;

    private static final class State {
        final Path file;
        final Map<UUID, Map<UUID, String>> lists = new LinkedHashMap<>();
        final Map<UUID, String> names = new LinkedHashMap<>();
        final Map<ServerPlayer, UiState> ui = new WeakHashMap<>();
        boolean writable = true;

        State(MinecraftServer server) {
            file = server.getWorldPath(LevelResource.ROOT).resolve("killstreakcod/teammates.json");
        }
    }

    private static final class UiState {
        long lastRequest;
        boolean resolving;
        RequestWhitelistPayload awaiting;
        WhitelistStatePayload cached;
    }

    private record ChangeResult(boolean success, String message, String detail) {
    }

    private FriendlyPlayers() {}

    public static boolean allied(ServerPlayer owner, Entity entity) {
        if (owner == entity || owner.isAlliedTo(entity) || entity.isAlliedTo(owner))
            return true;
        return entity instanceof ServerPlayer other
                && owner.serverLevel().getServer() == other.serverLevel().getServer()
                && listed(owner.serverLevel().getServer(), owner.getUUID(), other.getUUID());
    }

    static boolean allied(ServerPlayer owner, UUID other) {
        return other != null
                && (owner.getUUID().equals(other) || listed(owner.serverLevel().getServer(), owner.getUUID(), other));
    }

    private static boolean listed(MinecraftServer server, UUID first, UUID second) {
        State state = state(server);
        return state.lists.getOrDefault(first, Map.of()).containsKey(second)
                || state.lists.getOrDefault(second, Map.of()).containsKey(first);
    }

    private static State state(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, FriendlyPlayers::load);
    }

    private static State load(MinecraftServer server) {
        State state = new State(server);
        if (!Files.exists(state.file))
            return state;
        try (var reader = Files.newBufferedReader(state.file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1)
                throw new IOException("Unsupported teammate JSON schema");
            for (var row : root.getAsJsonObject("players").entrySet()) {
                UUID owner = UUID.fromString(row.getKey());
                JsonObject data = row.getValue().getAsJsonObject();
                Map<UUID, String> teammates = new LinkedHashMap<>();
                for (var value : data.getAsJsonArray("teammates")) {
                    var member = value.getAsJsonObject();
                    UUID id = UUID.fromString(member.get("uuid").getAsString());
                    if (!id.equals(owner))
                        teammates.put(id, member.get("name").getAsString());
                    if (teammates.size() > MAX_TEAMMATES)
                        throw new IOException("Teammate list exceeds limit");
                }
                state.names.put(owner, data.get("name").getAsString());
                state.lists.put(owner, teammates);
            }
        }
        catch (Exception failure) {
            state.lists.clear();
            state.names.clear();
            state.writable = false;
            com.mojang.logging.LogUtils.getLogger().error("Cannot load world teammate whitelist {}; file preserved",
                    state.file, failure);
        }
        return state;
    }

    private static void save(State state) throws IOException {
        if (!state.writable)
            throw new IOException("Existing teammate JSON could not be read; repair it and reload first");
        JsonObject root = new JsonObject(), players = new JsonObject();
        root.addProperty("schema", 1);
        root.add("players", players);
        for (var row : state.lists.entrySet()) {
            JsonObject data = new JsonObject();
            data.addProperty("name", state.names.getOrDefault(row.getKey(), row.getKey().toString()));
            JsonArray teammates = new JsonArray();
            for (var member : row.getValue().entrySet()) {
                JsonObject value = new JsonObject();
                value.addProperty("uuid", member.getKey().toString());
                value.addProperty("name", member.getValue());
                teammates.add(value);
            }
            data.add("teammates", teammates);
            players.add(row.getKey().toString(), data);
        }
        Files.createDirectories(state.file.getParent());
        Path staged = Files.createTempFile(state.file.getParent(), "teammates-", ".tmp");
        try {
            Files.writeString(staged,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n",
                    StandardCharsets.UTF_8);
            try {
                Files.move(staged, state.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, state.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static Map<UUID, Map<UUID, String>> copy(State state) {
        Map<UUID, Map<UUID, String>> previous = new LinkedHashMap<>();
        state.lists.forEach((owner, list) -> previous.put(owner, new LinkedHashMap<>(list)));
        return previous;
    }

    private static GameProfile resolveKnown(ServerPlayer player, String input, State state) {
        var server = player.serverLevel().getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(input);
        if (online != null)
            return online.getGameProfile();
        try {
            UUID id = UUID.fromString(input);
            online = server.getPlayerList().getPlayer(id);
            if (online != null)
                return online.getGameProfile();
            var cache = server.getProfileCache();
            GameProfile cached = cache == null ? null : cache.get(id).orElse(null);
            if (cached != null)
                return cached;
            String name = state.names.get(id);
            if (name == null)
                for (var list : state.lists.values())
                    if (list.containsKey(id)) {
                        name = list.get(id);
                        break;
                    }
            return new GameProfile(id, name == null ? input : name);
        }
        catch (IllegalArgumentException ignored) {
        }
        for (var entry : state.names.entrySet())
            if (entry.getValue().equalsIgnoreCase(input))
                return new GameProfile(entry.getKey(), entry.getValue());
        for (var row : state.lists.entrySet())
            for (var member : row.getValue().entrySet())
                if (member.getValue().equalsIgnoreCase(input))
                    return new GameProfile(member.getKey(), member.getValue());
        return null;
    }

    private static GameProfile resolve(ServerPlayer player, String input, State state) {
        GameProfile known = resolveKnown(player, input, state);
        if (known != null)
            return known;
        var server = player.serverLevel().getServer();
        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(input).orElse(null);
    }

    private static ChangeResult change(ServerPlayer player, State state, GameProfile member, boolean add) {
        if (!state.writable)
            return new ChangeResult(false, "message.killstreakcod.whitelist_io", "");
        if (member == null)
            return new ChangeResult(false, "message.killstreakcod.whitelist_unknown", "");
        if (member.getId().equals(player.getUUID()))
            return new ChangeResult(false, "message.killstreakcod.whitelist_self", "");
        Map<UUID, Map<UUID, String>> previous = copy(state);
        Map<UUID, String> previousNames = new LinkedHashMap<>(state.names);
        if (add) {
            Map<UUID, String> list = state.lists.computeIfAbsent(player.getUUID(), key -> new LinkedHashMap<>());
            if (!list.containsKey(member.getId()) && list.size() >= MAX_TEAMMATES)
                return new ChangeResult(false, "message.killstreakcod.whitelist_full", "");
            list.put(member.getId(), member.getName());
            state.names.put(player.getUUID(), player.getGameProfile().getName());
            state.names.put(member.getId(), member.getName());
        } else {
            Map<UUID, String> list = state.lists.get(player.getUUID());
            if (list != null)
                list.remove(member.getId());
            Map<UUID, String> reverse = state.lists.get(member.getId());
            if (reverse != null)
                reverse.remove(player.getUUID());
        }
        try {
            save(state);
        }
        catch (IOException failure) {
            state.lists.clear();
            state.lists.putAll(previous);
            state.names.clear();
            state.names.putAll(previousNames);
            com.mojang.logging.LogUtils.getLogger().error("Cannot save world teammate whitelist", failure);
            return new ChangeResult(false, "message.killstreakcod.whitelist_io", "");
        }
        state.ui.values().forEach(ui -> ui.cached = null);
        return new ChangeResult(true,
                add ? "message.killstreakcod.whitelist_added" : "message.killstreakcod.whitelist_removed",
                safeName(member.getName()));
    }

    private static int change(CommandSourceStack source, String input, boolean add) {
        ServerPlayer player = source.getPlayer();
        if (player == null)
            return 0;
        State state = state(player.serverLevel().getServer());
        GameProfile member = resolve(player, input, state);
        if (member == null) {
            source.sendFailure(Component.translatable("message.killstreakcod.whitelist_unknown", input));
            return 0;
        }
        ChangeResult result = change(player, state, member, add);
        Component message = Component.translatable(result.message, result.detail);
        if (result.success)
            source.sendSuccess(() -> message, false);
        else
            source.sendFailure(message);
        return result.success ? 1 : 0;
    }

    /**
     * All handlers run on the server thread and only use the authenticated caller's relationships.
     * 所有处理器都在服务端线程执行，仅使用已认证呼叫者自己的队友关系。
     */
    public static void uiRequest(ServerPlayer player, RequestWhitelistPayload request) {
        State state = state(player.serverLevel().getServer());
        UiState ui = state.ui.computeIfAbsent(player, key -> new UiState());
        if (ui.resolving)
            ui.awaiting = request;
        if (throttled(player, request, state, ui))
            return;
        sendUiState(player, request, state, ui.resolving ? "screen.killstreakcod.whitelist_pending"
                : state.writable ? "" : "message.killstreakcod.whitelist_io", "");
    }

    public static void uiAction(ServerPlayer player, WhitelistActionPayload action) {
        State state = state(player.serverLevel().getServer());
        UiState ui = state.ui.computeIfAbsent(player, key -> new UiState());
        if (ui.resolving)
            ui.awaiting = action.page();
        if (throttled(player, action.page(), state, ui))
            return;
        if (ui.resolving) {
            ui.awaiting = action.page();
            sendUiState(player, action.page(), state, "screen.killstreakcod.whitelist_pending", "");
            return;
        }
        if (!state.writable) {
            sendUiState(player, action.page(), state, "message.killstreakcod.whitelist_io", "");
            return;
        }
        String input = action.target().trim();
        GameProfile known = resolveKnown(player, input, state);
        if (known != null) {
            finishUiAction(player, action, state, known);
            return;
        }

        // 移除操作使用可见已存名单中的 UUID，不需要查询在线玩家档案。
        if (action.action() == WhitelistActionPayload.REMOVE || !input.matches("[A-Za-z0-9_]{1,16}")) {
            sendUiState(player, action.page(), state, "message.killstreakcod.whitelist_unknown", safeName(input));
            return;
        }
        var server = player.serverLevel().getServer();
        var cache = server.getProfileCache();
        if (cache == null) {
            sendUiState(player, action.page(), state, "message.killstreakcod.whitelist_unknown", safeName(input));
            return;
        }
        ui.resolving = true;
        ui.awaiting = action.page();
        try {

            // 档案查询不能阻塞内置服务器；每名玩家只允许一个查询，并设定截止时间。
            cache.getAsync(input).thenApply(value -> value).orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((profile, failure) -> server.execute(() -> {
                        if (WORLDS.get(server) != state || server.getPlayerList().getPlayer(player.getUUID()) != player)
                            return;
                        ui.resolving = false;
                        if (failure != null || profile == null || profile.isEmpty())
                            sendUiState(player, ui.awaiting == null ? action.page() : ui.awaiting, state,
                                    "message.killstreakcod.whitelist_unknown", safeName(input));
                        else
                            finishUiAction(player, action, state, profile.get());
                        ui.awaiting = null;
                    }));
        }
        catch (RuntimeException failure) {
            ui.resolving = false;
            ui.awaiting = null;
            sendUiState(player, action.page(), state, "message.killstreakcod.whitelist_unknown", safeName(input));
        }
    }

    private static void finishUiAction(ServerPlayer player, WhitelistActionPayload action, State state,
            GameProfile member) {
        ChangeResult result = change(player, state, member, action.action() == WhitelistActionPayload.ADD);
        UiState ui = state.ui.get(player);
        sendUiState(player, ui == null || ui.awaiting == null ? action.page() : ui.awaiting, state, result.message,
                result.detail);
    }

    private static boolean throttled(ServerPlayer player, RequestWhitelistPayload request, State state, UiState ui) {
        long now = System.nanoTime();
        if (ui.cached != null && ui.cached.pageSize() == request.pageSize() && now - ui.lastRequest < 150_000_000L) {
            WhitelistStatePayload p = ui.cached;
            StreakNetwork.send(player, new WhitelistStatePayload(request.request(), p.teammatePage(), p.teammatePages(),
                    p.teammateTotal(), p.onlinePage(), p.onlinePages(), p.onlineTotal(), p.pageSize(), p.writable(),
                    ui.resolving ? "screen.killstreakcod.whitelist_pending" : "screen.killstreakcod.whitelist_busy", "",
                    p.teammates(), p.online()));
            return true;
        }
        ui.lastRequest = now;
        return false;
    }

    private static Map<UUID, String> members(ServerPlayer player, State state) {
        Map<UUID, String> result = new LinkedHashMap<>(state.lists.getOrDefault(player.getUUID(), Map.of()));
        state.lists.forEach((owner, values) -> {
            if (values.containsKey(player.getUUID()))
                result.put(owner, state.names.getOrDefault(owner, owner.toString()));
        });
        return result;
    }

    private static void sendUiState(ServerPlayer player, RequestWhitelistPayload request, State state, String message,
            String detail) {
        var players = player.serverLevel().getServer().getPlayerList();
        Map<UUID, String> members = members(player, state);
        List<WhitelistStatePayload.Member> teammates = new ArrayList<>(), online = new ArrayList<>();
        members.forEach((id, name) -> {
            ServerPlayer active = players.getPlayer(id);
            teammates.add(new WhitelistStatePayload.Member(id,
                    safeName(active == null ? name : active.getGameProfile().getName()), active != null));
        });
        for (ServerPlayer candidate : players.getPlayers())
            if (candidate != player && !members.containsKey(candidate.getUUID()))
                online.add(new WhitelistStatePayload.Member(candidate.getUUID(),
                        safeName(candidate.getGameProfile().getName()), true));
        Comparator<WhitelistStatePayload.Member> order = Comparator
                .comparing(WhitelistStatePayload.Member::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WhitelistStatePayload.Member::uuid);
        teammates.sort(order);
        online.sort(order);
        int size = request.pageSize();
        int teammatePages = Math.max(1, (teammates.size() + size - 1) / size),
                onlinePages = Math.max(1, (online.size() + size - 1) / size);
        int teammatePage = Math.min(request.teammatePage(), teammatePages - 1),
                onlinePage = Math.min(request.onlinePage(), onlinePages - 1);
        WhitelistStatePayload payload = new WhitelistStatePayload(request.request(), teammatePage, teammatePages,
                teammates.size(), onlinePage, onlinePages, online.size(), size, state.writable, safeName(message),
                safeName(detail),
                teammates.subList(teammatePage * size, Math.min(teammates.size(), (teammatePage + 1) * size)),
                online.subList(onlinePage * size, Math.min(online.size(), (onlinePage + 1) * size)));
        state.ui.computeIfAbsent(player, key -> new UiState()).cached = payload;
        StreakNetwork.send(player, payload);
    }

    private static String safeName(String input) {
        if (input == null)
            return "";
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < input.length() && clean.length() < 64; i++)
            if (!Character.isISOControl(input.charAt(i)))
                clean.append(input.charAt(i));
        return clean.toString();
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null)
            return 0;
        State state = state(player.serverLevel().getServer());
        Map<UUID, String> members = members(player, state);
        source.sendSuccess(() -> Component.translatable("message.killstreakcod.whitelist_list",
                members.isEmpty() ? Component.translatable("message.killstreakcod.whitelist_empty")
                        : Component.literal(String.join(", ", members.values()))),
                false);
        return members.size();
    }

    private static int clear(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null)
            return 0;
        State state = state(player.serverLevel().getServer());
        var previous = copy(state);
        state.lists.remove(player.getUUID());
        state.lists.values().forEach(values -> values.remove(player.getUUID()));
        try {
            save(state);
        }
        catch (IOException failure) {
            state.lists.clear();
            state.lists.putAll(previous);
            source.sendFailure(Component.translatable("message.killstreakcod.whitelist_io"));
            return 0;
        }
        state.ui.values().forEach(ui -> ui.cached = null);
        source.sendSuccess(() -> Component.translatable("message.killstreakcod.whitelist_cleared"), false);
        return 1;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("killstreak").then(Commands.literal("whitelist")
                .requires(s -> s.getEntity() instanceof ServerPlayer)
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word()).suggests(
                        (c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b))
                        .executes(c -> change(c.getSource(), StringArgumentType.getString(c, "player"), true))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests((c, b) -> {
                            State state = state(c.getSource().getServer());
                            Set<String> names = new LinkedHashSet<>(state.names.values());
                            state.lists.values().forEach(list -> names.addAll(list.values()));
                            return SharedSuggestionProvider.suggest(names, b);
                        }).executes(c -> change(c.getSource(), StringArgumentType.getString(c, "player"), false))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    State loaded = load(c.getSource().getServer());
                    if (!loaded.writable) {
                        c.getSource().sendFailure(Component.translatable("message.killstreakcod.whitelist_io"));
                        return 0;
                    }
                    WORLDS.put(c.getSource().getServer(), loaded);
                    c.getSource().sendSuccess(() -> Component.translatable("message.killstreakcod.whitelist_reloaded"),
                            false);
                    return 1;
                }))));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WORLDS.remove(event.getServer());
    }
}
