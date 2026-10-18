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
        long unlocked, repairs;
        boolean streakMode, legacyAccess;

        boolean complete() {
            return tier == 4 && unlocked == ResearchCatalog.fullMask();
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
            progress.repairs = ResearchCatalog.loadNodes(tag.getList("Repairs", Tag.TAG_STRING));
            progress.streakMode = tag.getBoolean("StreakMode");
            progress.legacyAccess = tag.getBoolean("LegacyAccess");
            data.players.put(tag.getUUID("Player"), progress);
        }
        data.setDirty(false);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Schema", 1);
        root.putBoolean("LegacyWorld", legacyWorld);
        ListTag entries = new ListTag();
        players.forEach((id, progress) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", id);
            tag.putInt("Tier", progress.tier);
            tag.put("UnlockedNodes", ResearchCatalog.saveNodes(progress.unlocked));
            tag.put("Repairs", ResearchCatalog.saveNodes(progress.repairs));
            tag.putBoolean("StreakMode", progress.streakMode);
            tag.putBoolean("LegacyAccess", progress.legacyAccess);
            entries.add(tag);
        });
        root.put("Players", entries);
        return root;
    }
}
