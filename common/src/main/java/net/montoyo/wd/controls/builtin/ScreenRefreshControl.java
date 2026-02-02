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

public class ScreenRefreshControl extends ScreenControl {
    public static final ResourceLocation id = new ResourceLocation("webdisplays:screen_refresh");

    public ScreenRefreshControl() {
        super(id);
    }

    public ScreenRefreshControl(FriendlyByteBuf buf) {
        super(id);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
        checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.getSender());
        tes.refreshBrowser(side);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
        tes.refreshBrowser(side);
    }
}
