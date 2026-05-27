package net.montoyo.wd.controls.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.montoyo.wd.controls.ScreenControl;
import net.montoyo.wd.core.MissingPermissionException;
import net.montoyo.wd.core.ScreenRights;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.utilities.data.BlockSide;

import java.util.function.Function;

public class TestPatternControl extends ScreenControl {
    public static final ResourceLocation id = new ResourceLocation("webdisplays:test_pattern");

    private final boolean enabled;

    public TestPatternControl(boolean enabled) {
        super(id);
        this.enabled = enabled;
    }

    public TestPatternControl(FriendlyByteBuf buf) {
        super(id);
        enabled = buf.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx,
                             Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
        checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.getSender());
        tes.setTestPattern(side, enabled);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
        tes.applyTestPatternState(side, enabled);
        net.montoyo.wd.entity.ScreenData scr = tes.getScreen(side);
        if (scr != null && scr.isLinked() && scr.linkOrigin)
            net.montoyo.wd.utilities.link.LinkedScreenHelper.propagateTestPatternClient(tes, scr, enabled);
    }
}
