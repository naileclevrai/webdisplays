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
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.Log;
import net.montoyo.wd.utilities.data.BlockSide;

import java.util.function.Function;

public class VolumeControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("webdisplays:volume");
	
	float volume;
	boolean autoVolume;
	
	public VolumeControl(float volume, boolean autoVolume) {
		super(id);
		this.volume = volume;
		this.autoVolume = autoVolume;
	}
	
	public VolumeControl(FriendlyByteBuf buf) {
		super(id);
		volume = buf.readFloat();
		autoVolume = buf.readBoolean();
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeFloat(volume);
		buf.writeBoolean(autoVolume);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		checkPerms(ScreenRights.MANAGE_UPGRADES, permissionChecker, ctx.getSender());
		ScreenData scr = tes.getScreen(side);
		if (scr == null) {
			Log.error("Trying to set volume on invalid screen (side %s)", side.toString());
			return;
		}

		scr.volume = Math.max(0, Math.min(100, volume));
		scr.autoVolume = autoVolume;

		// Send update to all nearby clients
		net.montoyo.wd.net.WDNetworkRegistry.INSTANCE.send(
			net.minecraftforge.network.PacketDistributor.NEAR.with(() ->
				net.montoyo.wd.block.PeripheralBlock.point(tes.getLevel(), tes.getBlockPos())
			),
			net.montoyo.wd.net.client_bound.S2CMessageScreenUpdate.volume(tes, side, volume, autoVolume)
		);

		tes.setChanged();
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		ScreenData scr = tes.getScreen(side);
		if (scr == null) {
			Log.error("Trying to set volume on invalid screen (side %s)", side.toString());
			return;
		}

		scr.volume = Math.max(0, Math.min(100, volume));
		scr.autoVolume = autoVolume;
		// Volume is now handled by WebDisplaysAudioHandler
	}
}

