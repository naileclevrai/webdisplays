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
import net.montoyo.wd.utilities.data.Rotation;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.math.Vector2i;

import java.util.function.Function;

public class ScreenModifyControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("webdisplays:mod_screen");
	
	public enum ControlType {
		RESOLUTION, ROTATION, SHAPE_MODE, SIZE
	}

	ControlType type;
	Vector2i res;
	Vector2i size;
	Rotation rotation;
	ScreenShapeMode shapeMode;
	
	public ScreenModifyControl(Vector2i res) {
		super(id);
		this.type = ControlType.RESOLUTION;
		this.res = res;
	}

	public ScreenModifyControl(ControlType type, Vector2i vec) {
		super(id);
		this.type = type;
		if (type == ControlType.RESOLUTION)
			this.res = vec;
		else if (type == ControlType.SIZE)
			this.size = vec;
		else
			throw new IllegalArgumentException("Invalid control type " + type);
	}
	
	public ScreenModifyControl(Rotation rotation) {
		super(id);
		this.type = ControlType.ROTATION;
		this.rotation = rotation;
	}

	public ScreenModifyControl(ScreenShapeMode mode) {
		super(id);
		this.type = ControlType.SHAPE_MODE;
		this.shapeMode = mode;
	}

	public ScreenModifyControl(FriendlyByteBuf buf) {
		super(id);
		type = ControlType.values()[buf.readByte()];
		switch (type) {
			case RESOLUTION -> res = new Vector2i(buf);
			case ROTATION -> rotation = Rotation.values()[buf.readByte()];
			case SHAPE_MODE -> {
				byte mode = buf.readByte();
				ScreenShapeMode[] modes = ScreenShapeMode.values();
				shapeMode = (mode >= 0 && mode < modes.length) ? modes[mode] : ScreenShapeMode.NONE;
			}
			case SIZE -> size = new Vector2i(buf);
		}
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		switch (type) {
			case RESOLUTION -> res.writeTo(buf);
			case ROTATION -> buf.writeByte(rotation.ordinal());
			case SHAPE_MODE -> buf.writeByte(shapeMode.ordinal());
			case SIZE -> size.writeTo(buf);
		}
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.getSender());
		switch (type) {
			case RESOLUTION -> tes.setResolution(side, res);
			case ROTATION -> tes.setRotation(side, rotation);
			case SHAPE_MODE -> tes.setShapeMode(side, shapeMode);
			case SIZE -> tes.setSize(side, size);
		}
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		switch (type) {
			case RESOLUTION -> tes.setResolution(side, res);
			case ROTATION -> tes.setRotation(side, rotation);
			case SHAPE_MODE -> tes.setShapeMode(side, shapeMode);
			case SIZE -> tes.setSize(side, size);
		}
	}
}
