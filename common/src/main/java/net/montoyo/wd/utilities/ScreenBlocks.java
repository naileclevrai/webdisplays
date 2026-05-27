package net.montoyo.wd.utilities;

import net.minecraft.world.level.block.state.BlockState;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.utilities.data.ScreenPieceType;

public final class ScreenBlocks {
    private ScreenBlocks() {
    }

    public static boolean isScreen(BlockState state) {
        return state.getBlock() instanceof ScreenBlock;
    }

    public static ScreenPieceType getPiece(BlockState state) {
        if (state.getBlock() instanceof ScreenBlock)
            return state.getValue(ScreenBlock.piece);
        return ScreenPieceType.FULL;
    }
}
