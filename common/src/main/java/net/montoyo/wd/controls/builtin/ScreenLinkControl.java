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
import net.montoyo.wd.utilities.data.ScreenLinkMode;

import java.util.function.Function;

public class ScreenLinkControl extends ScreenControl {
    public static final ResourceLocation id = new ResourceLocation("webdisplays:link_screen");

    private final String linkId;
    private final ScreenLinkMode mode;
    private final boolean origin;

    public ScreenLinkControl(String linkId, ScreenLinkMode mode, boolean origin) {
        super(id);
        this.linkId = linkId == null ? "" : linkId;
        this.mode = mode == null ? ScreenLinkMode.JOINED : mode;
        this.origin = origin;
    }

    public ScreenLinkControl(FriendlyByteBuf buf) {
        super(id);
        linkId = buf.readUtf();
        mode = ScreenLinkMode.values()[buf.readByte() & 1];
        origin = buf.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(linkId);
        buf.writeByte(mode.ordinal());
        buf.writeBoolean(origin);
    }

    @Override
    public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
        checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.getSender());
        tes.setLink(side, linkId, mode, origin);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
        tes.setLink(side, linkId, mode, origin);
    }
}
