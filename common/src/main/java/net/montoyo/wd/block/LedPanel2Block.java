package net.montoyo.wd.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.montoyo.wd.utilities.data.ScreenPieceType;
import org.jetbrains.annotations.NotNull;

/**
 * Panneau LED plat (modèle Theatrical Extra Lights) — même logique WebDisplays, sans coins courbes / escalier diagonal.
 */
public class LedPanel2Block extends ScreenBlock {

    /** Face du bloc sur laquelle le panneau est accroché (sol, plafond ou mur). */
    public static final DirectionProperty MOUNT = DirectionProperty.create("mount",
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN);

    private static final float DEPTH = 6.5f / 16f;
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 0, 16, 16, DEPTH * 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 16 - DEPTH * 16, 16, 16, 16);
    private static final VoxelShape SHAPE_EAST = Block.box(16 - DEPTH * 16, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.box(0, 0, 0, DEPTH * 16, 16, 16);
    private static final VoxelShape SHAPE_UP = Block.box(0, 0, 0, 16, DEPTH * 16, 16);
    private static final VoxelShape SHAPE_DOWN = Block.box(0, 16 - DEPTH * 16, 0, 16, 16, 16);

    public LedPanel2Block(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(MOUNT, Direction.NORTH)
                .setValue(piece, ScreenPieceType.FULL));
    }

    public LedPanel2Block() {
        this(BlockBehaviour.Properties.copy(Blocks.STONE).noOcclusion());
    }

    @Override
    public boolean isFlatPanel() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(piece, ScreenPieceType.FULL)
                .setValue(MOUNT, mount)
                .setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public @NotNull VoxelShape getOcclusionShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return Shapes.empty();
    }
}
