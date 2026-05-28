package net.montoyo.wd.utilities.link;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.Log;
import net.montoyo.wd.utilities.Multiblock;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.ScreenLinkMode;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Active automatiquement un escalier d'écrans 45° comme un seul pan diagonal (link interne invisible).
 */
public final class DiagonalStaircaseHelper {
    private DiagonalStaircaseHelper() {
    }

    private static final class Column {
        final BlockPos anchor;
        final Vector3i tePos;
        final Vector2i size;

        Column(BlockPos anchor, Vector3i tePos, Vector2i size) {
            this.anchor = anchor;
            this.tePos = tePos;
            this.size = size;
        }
    }

    /**
     * @return true si la chaîne diagonale a été traitée (activation ou erreur affichée)
     */
    public static boolean tryActivate(Level world, BlockPos clicked, BlockSide side, Player player) {
        BlockState clickedState = world.getBlockState(clicked);
        if (clickedState.getBlock() instanceof ScreenBlock sb && sb.isFlatPanel())
            return false;

        List<BlockPos> chain = DiagonalCornerHelper.findDiagonalChain(world, clicked, side);
        if (chain.size() < 2)
            return false;

        List<Column> columns = new ArrayList<>(chain.size());
        for (BlockPos anchor : chain) {
            ScreenBlockEntity existing = getScreenEntity(world, anchor);
            if (existing != null && existing.getScreen(side) != null)
                return false;

            Vector3i tePos = new Vector3i(anchor);
            Vector2i size = Multiblock.measure(world, tePos, side);
            if (size.x < 1 || size.y < 1)
                return false;
            columns.add(new Column(anchor, tePos, size));
        }

        int height = columns.get(0).size.y;
        int totalWidth = 0;
        for (Column col : columns) {
            if (col.size.y != height)
                return false;
            totalWidth += col.size.x;
        }

        if (totalWidth < 2 && height < 2)
            return false;

        if (totalWidth > CommonConfig.Screen.maxScreenSizeX || height > CommonConfig.Screen.maxScreenSizeY)
            return false;

        BlockPos originAnchor = chain.stream()
                .min(Comparator.comparingInt(p -> chainSortKey(p, side)))
                .orElse(columns.get(0).anchor);
        String linkId = diagonalLinkId(originAnchor, side);

        Log.info("Player %s activated diagonal staircase at %s (%d columns, %dx%d canvas)",
                player.getName(), originAnchor, columns.size(), totalWidth, height);

        ScreenBlockEntity originTe = null;
        ScreenData originScr = null;

        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            ScreenBlockEntity te = ensureTileEntity(world, col.tePos);
            te.addScreen(side, col.size, null, player, true);

            boolean isOrigin = col.anchor.equals(originAnchor);
            te.setLink(side, linkId, ScreenLinkMode.JOINED, isOrigin);
            if (isOrigin) {
                originTe = te;
                originScr = te.getScreen(side);
            }
        }

        if (originTe != null && originScr != null) {
            LinkedScreenHelper.propagateResolutionFromOrigin(world, originTe, originScr, originScr.resolution);
        }

        return true;
    }

    private static String diagonalLinkId(BlockPos origin, BlockSide side) {
        return DiagonalCornerHelper.AUTO_LINK_PREFIX + origin.getX() + ':' + origin.getY() + ':' + origin.getZ() + ':' + side.ordinal();
    }

    private static int chainSortKey(BlockPos p, BlockSide side) {
        return p.getX() * side.right.x + p.getY() * side.right.y + p.getZ() * side.right.z
                + p.getX() * side.forward.x + p.getY() * side.forward.y + p.getZ() * side.forward.z;
    }

    private static ScreenBlockEntity getScreenEntity(Level world, BlockPos anchor) {
        BlockEntity be = world.getBlockEntity(anchor);
        return be instanceof ScreenBlockEntity te ? te : null;
    }

    private static ScreenBlockEntity ensureTileEntity(Level world, Vector3i tePos) {
        BlockPos bp = tePos.toBlock();
        BlockEntity be = world.getBlockEntity(bp);
        if (be instanceof ScreenBlockEntity te)
            return te;

        BlockState state = world.getBlockState(bp);
        world.setBlockAndUpdate(bp, state.setValue(ScreenBlock.hasTE, true));
        return (ScreenBlockEntity) world.getBlockEntity(bp);
    }
}
