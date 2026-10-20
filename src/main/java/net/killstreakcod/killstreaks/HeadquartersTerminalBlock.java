package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.HeadquartersTerminalMenu;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block communications station; both halves open the menu anchored to its base.
 * 两格高的总部通讯终端；上下两部分均打开绑定到底座的菜单。
 */
public final class HeadquartersTerminalBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<HeadquartersTerminalBlock> CODEC = simpleCodec(HeadquartersTerminalBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape[] LOWER_SHAPES = rotatedShapes(
            Shapes.or(box(0, 0, 0, 16, 1, 16), box(1, 1, 2, 15, 16, 15)));
    private static final VoxelShape[] UPPER_SHAPES = rotatedShapes(
            Shapes.or(box(0, 0, 0, 16, 1, 16), box(.8, 1, .2, 15.2, 3.5, 12), box(1.2, 3.4, 10.3, 14.8, 14.2, 14.5)));

    public HeadquartersTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context))
            return null;
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER
                : below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction partner = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == partner)
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? neighbor.setValue(HALF, half)
                    : Blocks.AIR.defaultBlockState();
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Only the lower half has loot; breaking the upper half settles that drop once before unlinking it.
        // 只有底座配置掉落物；拆除上半部分时先结算一次底座掉落，再解除上下连接。
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos base = pos.below();
            BlockState lower = level.getBlockState(base);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(lower, level, base)) {
                    dropResources(lower, level, base, null, player, player.getMainHandItem());
                }
                level.setBlock(base, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, base, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            if (!level.getBlockState(base).is(this))
                return InteractionResult.PASS;
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new HeadquartersTerminalMenu(id,
                    ContainerLevelAccess.create(level, base)), getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[] shapes = state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES;
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    private static VoxelShape[] rotatedShapes(VoxelShape north) {
        VoxelShape[] result = new VoxelShape[4];
        VoxelShape current = north;
        for (int step = 0; step < 4; step++) {
            Direction facing = Direction.from2DDataValue((Direction.NORTH.get2DDataValue() + step) % 4);
            result[facing.get2DDataValue()] = current;
            VoxelShape rotated = Shapes.empty();
            for (var bounds : current.toAabbs()) {
                rotated = Shapes.or(rotated, Shapes.box(1 - bounds.maxZ, bounds.minY, bounds.minX, 1 - bounds.minZ,
                        bounds.maxY, bounds.maxX));
            }
            current = rotated.optimize();
        }
        return result;
    }
}
