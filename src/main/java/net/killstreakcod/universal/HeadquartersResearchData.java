package net.killstreakcod.universal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * World-owned player research; death, dimension changes and block replacement do not erase it.
 * 研究归属世界内的玩家 UUID；死亡、换维度和重新放置终端均不丢失进度。
 */
final class HeadquartersResearchData extends SavedData {
    static final class Progress {
        int tier;
        ResearchMask unlocked = ResearchMask.EMPTY, equipped = ResearchMask.EMPTY, repairs = ResearchMask.EMPTY;
        final Map<String, Integer> requisitions = new LinkedHashMap<>();
        boolean streakMode, legacyAccess, modeUnlocked;

        boolean complete() {
            return tier == 4 && unlocked.equals(ResearchCatalog.fullMask());
        }

        boolean canSwitch() {
            return legacyAccess || modeUnlocked || complete();
        }
    }

    private final boolean legacyWorld;
    private final Map<UUID, Progress> players = new LinkedHashMap<>();

    private HeadquartersResearchData(boolean legacyWorld) {
        this.legacyWorld = legacyWorld;
        setDirty();
    }

    static HeadquartersResearchData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> new HeadquartersResearchData(legacyWorld(server)), HeadquartersResearchData::load),
                "killstreakcod_research");
    }

    Progress player(ServerPlayer player) {
        return players.computeIfAbsent(player.getUUID(), id -> {
            Progress progress = new Progress();
            progress.legacyAccess = legacyWorld || oldTags(player);
            progress.streakMode = progress.legacyAccess || player.isCreative();
            setDirty();
            return progress;
        });
    }

    private static boolean oldTags(ServerPlayer player) {
        return player.getTags().stream().anyMatch(tag -> tag.startsWith("killstreakcod."));
    }

    private static boolean legacyWorld(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().stream().anyMatch(HeadquartersResearchData::oldTags)
                || Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve("killstreakcod/teammates.json")))
            return true;
        Path directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(directory))
            return false;
        // Inspect saved player tags once, before the new login creates any ledger tags.
        // 只在首次创建研究存档时扫描旧玩家标签，且先于新登录生成奖励账本标签。
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                try {
                    CompoundTag player = NbtIo.readCompressed(file, NbtAccounter.create(16 * 1024 * 1024L));
                    for (Tag tag : player.getList("Tags", Tag.TAG_STRING))
                        if (tag.getAsString().startsWith("killstreakcod."))
                            return true;
                }
                catch (IOException | RuntimeException failure) {
                    LogUtils.getLogger().warn("Could not inspect legacy research defaults in {}", file, failure);
                }
            }
        }
        catch (IOException failure) {
            LogUtils.getLogger().warn("Could not inspect legacy player directory {}", directory, failure);
        }
        return false;
    }

    private static HeadquartersResearchData load(CompoundTag root, HolderLookup.Provider registries) {
        HeadquartersResearchData data = new HeadquartersResearchData(root.getBoolean("LegacyWorld"));
        for (Tag entry : root.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) entry;
            if (!tag.hasUUID("Player"))
                continue;
            Progress progress = new Progress();
            progress.tier = Math.clamp(tag.getInt("Tier"), 0, 4);
            progress.unlocked = ResearchCatalog.loadNodes(tag.getList("UnlockedNodes", Tag.TAG_STRING));
            // Older saves applied every researched upgrade; retain that behavior until the player removes one.
            // 旧存档默认应用全部已研究升级；迁移时保持装配，直到玩家主动卸下。
            progress.equipped = tag.contains("EquippedNodes", Tag.TAG_LIST) ? ResearchCatalog
                    .loadNodes(tag.getList("EquippedNodes", Tag.TAG_STRING)).intersect(progress.unlocked)
                    : progress.unlocked;
            for (ResearchCatalog.Node node : ResearchCatalog.nodes())
                if (node.base())
                    progress.equipped = progress.equipped.union(progress.unlocked.intersect(node.bit()));
            progress.repairs = ResearchCatalog.loadNodes(tag.getList("Repairs", Tag.TAG_STRING));
            CompoundTag requisitions = tag.getCompound("Requisitions");
            for (ResearchCatalog.Node node : ResearchCatalog.nodes())
                if (node.base() && requisitions.getInt(node.id()) > 0)
                    progress.requisitions.put(node.id(), requisitions.getInt(node.id()));
            progress.streakMode = tag.getBoolean("StreakMode");
            progress.legacyAccess = tag.getBoolean("LegacyAccess");
            // Keep a mode earned by completing the previous 38-node tree when the tree expands.
            // 科技树扩充后，保留完成旧版 38 项研究取得的连杀模式资格。
            ResearchMask previousTree = ResearchMask.first(38);
            progress.modeUnlocked = tag.getBoolean("ModeUnlocked") || progress.complete() || root.getInt("Schema") < 3
                    && progress.tier == 4 && progress.unlocked.intersect(previousTree).equals(previousTree);
            data.players.put(tag.getUUID("Player"), progress);
        }
        data.setDirty(false);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Schema", 4);
        root.putBoolean("LegacyWorld", legacyWorld);
        ListTag entries = new ListTag();
        players.forEach((id, progress) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", id);
            tag.putInt("Tier", progress.tier);
            tag.put("UnlockedNodes", ResearchCatalog.saveNodes(progress.unlocked));
            tag.put("EquippedNodes", ResearchCatalog.saveNodes(progress.equipped.intersect(progress.unlocked)));
            tag.put("Repairs", ResearchCatalog.saveNodes(progress.repairs));
            // Stable reward IDs keep paid reserves independent of catalog order and player respawns.
            // 按稳定奖励 ID 保存已付款储备，目录调整和玩家重生均不改变其归属。
            CompoundTag requisitions = new CompoundTag();
            progress.requisitions.forEach((reward, count) -> {
                if (count > 0)
                    requisitions.putInt(reward, count);
            });
            tag.put("Requisitions", requisitions);
            tag.putBoolean("StreakMode", progress.streakMode);
            tag.putBoolean("LegacyAccess", progress.legacyAccess);
            tag.putBoolean("ModeUnlocked", progress.modeUnlocked || progress.complete());
            entries.add(tag);
        });
        root.put("Players", entries);
        return root;
    }
}
