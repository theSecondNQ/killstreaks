package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.HeadquartersTerminalBlock;
import net.killstreakcod.killstreaks.TacticalEquipmentItem;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.network.StreamCodec;

import java.util.Arrays;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KillstreakcodMod.MODID)
@PrefixGameTestTemplate(false)
public final class ForgePortGameTests {
    private ForgePortGameTests() {}

    @GameTest(template = "forge_smoke")
    public static void packetsRoundTrip(GameTestHelper helper) {
        roundTrip(helper, UseKillstreakPayload.STREAM_CODEC, new UseKillstreakPayload(4));
        roundTrip(helper, SaveKillstreakLoadoutPayload.STREAM_CODEC,
                new SaveKillstreakLoadoutPayload("uav", "vtol", "gunship", "rangers"));
        roundTrip(helper, ResearchActionPayload.STREAM_CODEC,
                new ResearchActionPayload(17, ResearchActionPayload.PREPARE, "precision"));
        roundTrip(helper, TacticalControlPayload.STREAM_CODEC,
                new TacticalControlPayload(120.5F, -22.5F, 1, -.5F, TacticalControlPayload.FIRE | TacticalControlPayload.EXIT, 823));
        roundTrip(helper, WhitelistActionPayload.STREAM_CODEC,
                new WhitelistActionPayload(UUID.randomUUID(), WhitelistActionPayload.ADD, "Operator", 0, 0, 8));
        roundTrip(helper, TacticalViewPayload.STREAM_CODEC,
                new TacticalViewPayload(42, "gunship", 1200, 1, 100, true));
        roundTrip(helper, RadarPayload.STREAM_CODEC, new RadarPayload(true, false, false, new int[] { 1, 2, 3, 4 }));
        int count = net.killstreakcod.killstreaks.StreakReward.SELECTABLE.size();
        roundTrip(helper, ResearchStatePayload.STREAM_CODEC,
                new ResearchStatePayload(7, 4, ResearchMask.first(79), ResearchMask.single(70), ResearchMask.EMPTY,
                        true, true, false, false, "", new int[count], 2, false, new int[count], 3));
        helper.succeed();
    }

    @GameTest(template = "forge_smoke")
    public static void invalidPacketsRejected(GameTestHelper helper) {
        boolean badPage = false;
        try {
            new RequestWhitelistPayload(UUID.randomUUID(), -1, 0, 8);
        }
        catch (IllegalArgumentException expected) {
            badPage = true;
        }
        helper.assertTrue(badPage, "Negative whitelist page accepted");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeVarInt(ResearchActionPayload.RESEARCH);
            buffer.writeUtf("x".repeat(65));
            boolean rejected = false;
            try {
                ResearchActionPayload.STREAM_CODEC.decode(buffer);
            }
            catch (io.netty.handler.codec.DecoderException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Oversized research target accepted");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "forge_smoke")
    public static void terminalAndRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "port-test"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(Vec3.atCenterOf(base).add(0, 0, 1));
        ItemStack item = KillstreakContent.HEADQUARTERS_TERMINAL_ITEM.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(base.below()).add(0, .5, 0), Direction.UP,
                base.below(), false);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        var placement = new net.minecraft.world.item.context.BlockPlaceContext(use);
        helper.assertTrue(KillstreakContent.HEADQUARTERS_TERMINAL.get().getStateForPlacement(placement) != null,
                "Terminal placement state rejected: floor=" + level.getBlockState(base.below())
                        + ", base=" + level.getBlockState(base) + ", above=" + level.getBlockState(base.above()));
        helper.assertTrue(placement.canPlace(), "BlockPlaceContext rejected the target cell");
        helper.assertTrue(player.mayUseItemAt(placement.getClickedPos(), placement.getClickedFace(), item),
                "Test player may not use the terminal item here");
        var placedState = KillstreakContent.HEADQUARTERS_TERMINAL.get().getStateForPlacement(placement);
        helper.assertTrue(level.isUnobstructed(placedState, placement.getClickedPos(),
                net.minecraft.world.phys.shapes.CollisionContext.of(player)), "Terminal placement collision");
        var result = item.useOn(use);
        helper.assertTrue(result.consumesAction(), "Terminal could not be placed: " + result);
        var block = KillstreakContent.HEADQUARTERS_TERMINAL.get();
        helper.assertTrue(level.getBlockState(base).is(block), "Terminal base missing");
        helper.assertTrue(level.getBlockState(base.above()).is(block), "Terminal upper half missing");
        helper.assertTrue(level.getBlockState(base.above()).getValue(HeadquartersTerminalBlock.HALF)
                == DoubleBlockHalf.UPPER, "Terminal half state changed");
        var menu = new HeadquartersTerminalMenu(1, ContainerLevelAccess.create(level, base));
        helper.assertTrue(menu.stillValid(player), "Nearby terminal menu is invalid");
        helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                KillstreakcodMod.MODID, "headquarters_terminal")).isPresent(), "Terminal recipe was not loaded");
        level.destroyBlock(base, false);
        helper.assertFalse(level.getBlockState(base.above()).is(block), "Upper half remained after base removal");
        helper.assertFalse(menu.stillValid(player), "Menu remained valid after terminal removal");
        helper.succeed();
    }

    @GameTest(template = "forge_smoke")
    public static void entityStateAndItemTags(GameTestHelper helper) {
        for (var kind : TacticalUnitEntity.Kind.values()) {
            var unit = KillstreakcodMod.TACTICAL_UNIT.get().create(helper.getLevel());
            helper.assertTrue(unit != null, "Tactical entity factory failed");
            unit.kind(kind);
            unit.stage(3);
            unit.lifetime = 400;
            var saved = new CompoundTag();
            unit.saveWithoutId(saved);
            var restored = KillstreakcodMod.TACTICAL_UNIT.get().create(helper.getLevel());
            restored.load(saved);
            int expectedStage = kind == TacticalUnitEntity.Kind.ROCKET ? 7 : 3;
            helper.assertTrue(restored.kind() == kind && restored.stage() == expectedStage && restored.lifetime == 400,
                    "Tactical entity state did not survive NBT: " + kind);
            helper.assertTrue(restored.getBbWidth() > 0 && restored.getEyeHeight() > 0, "Invalid entity dimensions");
        }
        ItemStack equipment = TacticalEquipmentItem.stack("designator", "precision_airstrike");
        ItemStack restored = ItemStack.of(equipment.save(new CompoundTag()));
        helper.assertTrue(restored.getOrCreateTag().getString("Reward").equals("precision_airstrike"),
                "Reward selection was lost in the Forge item tag");
        helper.succeed();
    }

    @GameTest(template = "forge_smoke")
    public static void researchWritesWorldData(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "port-test"));
        var data = HeadquartersResearchData.get(server);
        var progress = data.player(player);
        progress.tier = 4;
        progress.unlocked = ResearchCatalog.fullMask();
        progress.equipped = ResearchMask.single(70);
        progress.requisitions.put("uav", 3);
        data.setDirty();
        server.overworld().getDataStorage().save();
        var file = server.getWorldPath(LevelResource.ROOT).resolve("data/killstreakcod_research.dat");
        var saved = NbtIo.readCompressed(file.toFile()).getCompound("data");
        helper.assertTrue(saved.getInt("Schema") == 4, "Research save schema changed");
        boolean found = false;
        for (var entry : saved.getList("Players", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            if (!player.getUUID().equals(tag.getUUID("Player")))
                continue;
            helper.assertTrue(tag.getInt("Tier") == 4 && tag.getCompound("Requisitions").getInt("uav") == 3,
                    "Research tier or paid reserves were lost");
            helper.assertTrue(ResearchCatalog.loadNodes(tag.getList("UnlockedNodes", Tag.TAG_STRING))
                    .equals(ResearchCatalog.fullMask()), "Research beyond bit 64 was lost");
            found = true;
        }
        helper.assertTrue(found, "Research was not saved under the player's UUID");
        helper.succeed();
    }

    private static <T> void roundTrip(GameTestHelper helper, StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf repeated = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(encoded, payload);
            byte[] expected = new byte[encoded.readableBytes()];
            encoded.getBytes(0, expected);
            T decoded = codec.decode(encoded);
            helper.assertTrue(encoded.readableBytes() == 0, "Packet decoder left trailing bytes");
            codec.encode(repeated, decoded);
            byte[] actual = new byte[repeated.readableBytes()];
            repeated.readBytes(actual);
            helper.assertTrue(Arrays.equals(expected, actual), "Packet changed after a round trip: " + payload.getClass());
        } finally {
            encoded.release();
            repeated.release();
        }
    }
}
