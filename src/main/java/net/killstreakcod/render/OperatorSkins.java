package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.killstreakcod.render.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Local cinematic costumes. Files are decoded only on selection and cached between frames.
 * 管理本地过场人物外观，仅在选择皮肤时解码文件，并在帧之间缓存。
 */
public final class OperatorSkins {
    public enum Role {
        CHOPPER_GUNNER, GUNSHIP_OPERATOR, GUNSHIP_COPILOT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Selection(String file, boolean slim) {
        public Selection {
            if (!safeFile(file))
                throw new IllegalArgumentException("Invalid local skin filename");
        }
    }

    private static final int MAX_BYTES = 1024 * 1024, MAX_FILES = 256;
    private static final Selection DEFAULT = new Selection("", false);
    private static final EnumMap<Role, Selection> SELECTED = new EnumMap<>(Role.class);
    private static final Map<String, Optional<ResourceLocation>> TEXTURES = new HashMap<>();
    private static List<String> files = List.of();
    private static int rejected;
    private static boolean initialized;

    private OperatorSkins() {}

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("killstreakcod-skins");
    }

    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/killstreakcod-operator-skins.json");
    }

    private static boolean safeFile(String file) {
        return file != null && file.length() <= 255
                && (file.isEmpty() || !file.contains("/") && !file.contains("\\") && !file.contains(":")
                        && !file.equals(".") && !file.equals("..") && file.toLowerCase(Locale.ROOT).endsWith(".png")
                        && file.chars().noneMatch(Character::isISOControl));
    }

    public static void initialize() {
        if (initialized)
            return;
        initialized = true;
        for (Role role : Role.values())
            SELECTED.put(role, DEFAULT);
        try {
            prepareDirectory();
            Path config = configFile();
            if (!Files.exists(config))
                return;
            if (Files.size(config) > 65536)
                throw new IOException("Skin preferences exceed 64 KiB");
            JsonObject json;
            try (var input = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(input).getAsJsonObject();
            }
            if (json.get("schema").getAsInt() != 1)
                throw new IOException("Unsupported skin preference schema");
            EnumMap<Role, Selection> loaded = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                JsonObject row = json.getAsJsonObject("roles").getAsJsonObject(role.key());
                loaded.put(role, new Selection(row.get("file").getAsString(), row.get("slim").getAsBoolean()));
            }
            SELECTED.putAll(loaded);
        }
        catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot read local cinematic skin preferences; file preserved",
                    failure);
        }
    }

    private static void prepareDirectory() throws IOException {
        Files.createDirectories(directory());
        Path instructions = directory().resolve("README.txt");
        if (!Files.exists(instructions))
            Files.writeString(instructions,
                    "将 Minecraft PNG 皮肤放在此文件夹，然后在连杀终端 → 人物皮肤中点击刷新。\n支持 64×64 和旧式 64×32；每张最大 1 MiB，最多读取 256 个 PNG。\n可分别选择直升机炮手、炮艇操作员 1 和炮艇操作员 2，并选择普通／纤细手臂。\n选择存于游戏目录 config/killstreakcod-operator-skins.json。\nPut 64x64 or legacy 64x32 Minecraft PNG skins here. Open Killstreak Terminal > Operator Skins > Refresh.\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public static void refresh() throws IOException {
        initialize();
        prepareDirectory();
        invalidateTextures();
        rejected = 0;
        List<String> accepted = new ArrayList<>();
        try (var stream = Files.list(directory())) {
            for (Path p : stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_FILES).toList()) {
                try {
                    if (!safeFile(p.getFileName().toString()) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                            || Files.size(p) > MAX_BYTES)
                        throw new IOException("Unsupported skin file");
                    try (var input = Files.newInputStream(p)) {
                        checkHeader(input.readNBytes(24));
                    }
                    accepted.add(p.getFileName().toString());
                }
                catch (IOException | RuntimeException invalid) {
                    rejected++;
                }
            }
        }
        files = List.copyOf(accepted);
    }

    public static List<String> files() {
        return files;
    }

    public static int rejected() {
        return rejected;
    }

    public static EnumMap<Role, Selection> selections() {
        initialize();
        return new EnumMap<>(SELECTED);
    }

    public static void save(Map<Role, Selection> choices) throws IOException {
        JsonObject root = new JsonObject(), roles = new JsonObject();
        root.addProperty("schema", 1);
        root.add("roles", roles);
        for (Role role : Role.values()) {
            Selection value = Objects.requireNonNull(choices.get(role));
            JsonObject row = new JsonObject();
            row.addProperty("file", value.file);
            row.addProperty("slim", value.slim);
            roles.add(role.key(), row);
        }
        Path destination = configFile();
        Files.createDirectories(destination.getParent());
        Path stage = Files.createTempFile(destination.getParent(), "operator-skins-", ".tmp");
        try {
            Files.writeString(stage,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n",
                    StandardCharsets.UTF_8);
            try {
                Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(stage, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            SELECTED.putAll(choices);
        } finally {
            Files.deleteIfExists(stage);
        }
    }

    public static PlayerSkin skin(Role role) {
        initialize();
        return preview(SELECTED.getOrDefault(role, DEFAULT));
    }

    public static PlayerSkin preview(Selection selection) {
        var player = Minecraft.getInstance().player;
        PlayerSkin fallback = player == null
                ? new PlayerSkin(ResourceLocation.parse("textures/entity/player/wide/steve.png"), null,
                        null, null, PlayerSkin.Model.WIDE, false)
                : PlayerSkin.current(player);
        if (selection.file.isEmpty())
            return fallback;
        ResourceLocation texture = TEXTURES.computeIfAbsent(selection.file, OperatorSkins::loadTexture).orElse(null);
        return texture == null ? fallback
                : new PlayerSkin(texture, null, null, null,
                        selection.slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
    }

    public static boolean unavailable(Selection selection) {
        var texture = TEXTURES.get(selection.file);
        return !selection.file.isEmpty() && (!files.contains(selection.file) || texture != null && texture.isEmpty());
    }

    public static void invalidateTextures() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (var entry : TEXTURES.values())
            entry.ifPresent(manager::release);
        TEXTURES.clear();
    }

    private static Optional<ResourceLocation> loadTexture(String file) {
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            Path path = directory().resolve(file);
            if (!safeFile(file) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > MAX_BYTES)
                throw new IOException("Unsupported skin file");
            byte[] data;
            try (var input = Files.newInputStream(path)) {
                data = input.readNBytes(MAX_BYTES + 1);
            }
            if (data.length > MAX_BYTES)
                throw new IOException("Skin exceeds 1 MiB");
            checkHeader(data);
            image = NativeImage.read(new ByteArrayInputStream(data));
            image = normalize(image);
            texture = new DynamicTexture(image);
            image = null;
            texture.setFilter(false, false);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                    "operator_skins/" + UUID.nameUUIDFromBytes(file.getBytes(StandardCharsets.UTF_8)));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            texture = null;
            return Optional.of(location);
        }
        catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Cannot decode local operator skin {}; using the player's skin", file, failure);
            return Optional.empty();
        } finally {
            if (image != null)
                image.close();
            if (texture != null)
                texture.close();
        }
    }

    private static void checkHeader(byte[] bytes) throws IOException {
        if (bytes.length < 24)
            throw new IOException("Truncated PNG");
        ByteBuffer data = ByteBuffer.wrap(bytes);
        if (data.getLong() != 0x89504e470d0a1a0aL || data.getInt() != 13 || data.getInt() != 0x49484452)
            throw new IOException("Invalid PNG header");
        int width = data.getInt(), height = data.getInt();
        if (width != 64 || height != 32 && height != 64)
            throw new IOException("Skin must be 64x64 or 64x32");
    }

    /**
     * Match vanilla legacy limb mirroring and base-layer opacity for local textures.
     * 本地纹理遵循原版旧格式皮肤的肢体镜像与基础图层不透明度规则。
     */
    private static NativeImage normalize(NativeImage image) {
        boolean legacy = image.getHeight() == 32;
        if (legacy) {
            NativeImage square = new NativeImage(64, 64, true);
            square.copyFrom(image);
            image.close();
            image = square;
            square.fillRect(0, 32, 64, 32, 0);
            for (int[] region : new int[][] { { 4, 16, 16, 32, 4, 4 }, { 8, 16, 16, 32, 4, 4 },
                    { 0, 20, 24, 32, 4, 12 }, { 4, 20, 16, 32, 4, 12 }, { 8, 20, 8, 32, 4, 12 },
                    { 12, 20, 16, 32, 4, 12 }, { 44, 16, -8, 32, 4, 4 }, { 48, 16, -8, 32, 4, 4 },
                    { 40, 20, 0, 32, 4, 12 }, { 44, 20, -8, 32, 4, 12 }, { 48, 20, -16, 32, 4, 12 },
                    { 52, 20, -8, 32, 4, 12 } })
                square.copyRect(region[0], region[1], region[2], region[3], region[4], region[5], true, false);
            boolean transparent = false;
            for (int x = 32; x < 64; x++)
                for (int y = 0; y < 32; y++)
                    if ((square.getPixelRGBA(x, y) >>> 24) < 128)
                        transparent = true;
            if (!transparent)
                for (int x = 32; x < 64; x++)
                    for (int y = 0; y < 32; y++)
                        square.setPixelRGBA(x, y, square.getPixelRGBA(x, y) & 0xFFFFFF);
        }
        for (int[] rectangle : new int[][] { { 0, 0, 32, 16 }, { 0, 16, 64, 32 }, { 16, 48, 48, 64 } })
            for (int x = rectangle[0]; x < rectangle[2]; x++)
                for (int y = rectangle[1]; y < rectangle[3]; y++)
                    image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
        return image;
    }
}
