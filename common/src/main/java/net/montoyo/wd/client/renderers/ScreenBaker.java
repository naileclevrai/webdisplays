/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.ScreenPieceType;
import net.montoyo.wd.utilities.math.Vector3f;
import net.montoyo.wd.utilities.math.Vector3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ScreenBaker implements BakedModel {
	
	private static final List<BakedQuad> noQuads = ImmutableList.of();
	private final TextureAtlasSprite[] texs = new TextureAtlasSprite[16];
	private final BlockSide[] blockSides = BlockSide.values();
	private final Direction[] blockFacings = Direction.values();
	private final ModelState modelState;
	private final Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter;
	private final ItemOverrides overrides;
	private final ItemTransforms itemTransforms;
	private final ScreenPieceType defaultPiece;
	
	IntegerModelProperty[] TEXTURES = new IntegerModelProperty[6];
	
	public ScreenBaker(ModelState modelState, Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter, ItemOverrides overrides, ItemTransforms itemTransforms, ScreenPieceType defaultPiece) {
		this(modelState, spriteGetter, overrides, itemTransforms, defaultPiece, ScreenModelLoader.MATERIALS_SIDES);
	}

	public ScreenBaker(ModelState modelState, Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> spriteGetter, ItemOverrides overrides, ItemTransforms itemTransforms, ScreenPieceType defaultPiece, net.minecraft.client.resources.model.Material[] materials) {
		this.modelState = modelState;
		this.spriteGetter = spriteGetter;
		this.overrides = overrides;
		this.itemTransforms = itemTransforms;
		this.defaultPiece = defaultPiece;

		for (int i = 0; i < texs.length; i++) {
			texs[i] = spriteGetter.apply(materials[i]);
		}
		
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = new IntegerModelProperty();
		}
	}
	
	private void putVertex(int[] buf, int pos, Vector3f vpos, TextureAtlasSprite tex, Vector3f uv, Vector3i normal) {
		pos *= 8;
		
		buf[pos] = Float.floatToRawIntBits(vpos.x);
		buf[pos + 1] = Float.floatToRawIntBits(vpos.y);
		buf[pos + 2] = Float.floatToRawIntBits(vpos.z);
		buf[pos + 3] = 0xFFFFFFFF; //Color, let this white...
		buf[pos + 4] = Float.floatToRawIntBits(tex.getU(uv.x));
		buf[pos + 5] = Float.floatToRawIntBits(tex.getV(uv.y));
		
		int nx = (normal.x * 127) & 0xFF;
		int ny = (normal.y * 127) & 0xFF;
		int nz = (normal.z * 127) & 0xFF;
		buf[pos + 7] = nx | (ny << 8) | (nz << 16);
	}
	
	private Vector3f rotateVec(Vector3f vec, BlockSide side) {
		return switch (side) {
			case BOTTOM -> new Vector3f(vec.x, 1.0f, 1.0f - vec.z);
			case TOP -> new Vector3f(vec.x, 0.0f, vec.z);
			case NORTH -> new Vector3f(vec.x, vec.z, 1.0f);
			case SOUTH -> new Vector3f(vec.x, 1.0f - vec.z, 0.0f);
			case WEST -> new Vector3f(1.f, vec.x, vec.z);
			case EAST -> new Vector3f(0.0f, 1.0f - vec.x, vec.z);
			//noinspection UnnecessaryDefault
			default -> throw new RuntimeException("Unknown block side " + side);
		};
	}
	
	private Vector3f rotateTex(BlockSide side, float u, float v) {
		return switch (side) {
			case BOTTOM, NORTH -> new Vector3f(16.f - u, 16.f - v, 0.0f);
			case TOP -> new Vector3f(16.f - u, v, 0.0f);
			case SOUTH -> new Vector3f(u, v, 0.0f);
			case WEST -> new Vector3f(16.f - v, u, 0.0f);
			case EAST -> new Vector3f(v, 16.f - u, 0.0f);
			//noinspection UnnecessaryDefault
			default -> throw new RuntimeException("Unknown block side " + side);
		};
	}
	
	private BakedQuad bakeSide(BlockSide side, TextureAtlasSprite tex) {
		int[] data = new int[8 * 4];
		
		// I have no idea
		int rotation = switch (side) {
			case NORTH, TOP, BOTTOM -> 2;
			case SOUTH -> 0;
			case EAST -> 1;
			case WEST -> 3;
			//noinspection UnnecessaryDefault
			default -> throw new RuntimeException("Unknown block side " + side);
		};
		
		putVertex(data, (rotation + 3) % 4, rotateVec(new Vector3f(0.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 16.0f, 0.0f), side.backward);
		putVertex(data, (rotation + 2) % 4, rotateVec(new Vector3f(0.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 16.0f, 16.0f), side.backward);
		putVertex(data, (rotation + 1) % 4, rotateVec(new Vector3f(1.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 0.0f, 16.0f), side.backward);
		putVertex(data, (rotation) % 4, rotateVec(new Vector3f(1.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 0.0f, 0.0f), side.backward);
		
		return new BakedQuad(data, 0xFFFFFFFF, blockFacings[side.ordinal()].getOpposite(), tex, true);
	}

	private BakedQuad bakeTriangle(BlockSide side, TextureAtlasSprite tex,
	                               float x0, float z0, float x1, float z1, float x2, float z2) {
		int[] data = new int[8 * 4];
		putLocalVertex(data, 0, side, tex, x0, z0);
		putLocalVertex(data, 1, side, tex, x1, z1);
		putLocalVertex(data, 2, side, tex, x2, z2);
		putLocalVertex(data, 3, side, tex, x2, z2);

		return new BakedQuad(data, 0xFFFFFFFF, blockFacings[side.ordinal()].getOpposite(), tex, true);
	}

	private void putLocalVertex(int[] data, int slot, BlockSide side, TextureAtlasSprite tex, float localX, float localZ) {
		putVertex(data, slot,
				rotateVec(new Vector3f(localX, 0.0f, localZ), side),
				tex,
				rotateTex(side, 16.0f * (1.0f - localX), 16.0f * localZ),
				side.backward);
	}

	private List<BakedQuad> bakeShapedSide(BlockSide side, TextureAtlasSprite tex, ScreenPieceType piece) {
		if (piece == ScreenPieceType.FULL) {
			return List.of(bakeSide(side, tex));
		}

		List<BakedQuad> quads = new ArrayList<>();
		float x0 = 0.0f;
		float x1 = 1.0f;
		float y0 = 0.0f;
		float y1 = 1.0f;
		float xm = 0.5f;
		float ym = 0.5f;

		switch (piece) {
			case HALF_BOTTOM -> {
				quads.add(bakeTriangle(side, tex, x0, y0, x1, y0, x1, ym));
				quads.add(bakeTriangle(side, tex, x0, y0, x1, ym, x0, ym));
			}
			case HALF_TOP -> {
				quads.add(bakeTriangle(side, tex, x0, ym, x1, ym, x1, y1));
				quads.add(bakeTriangle(side, tex, x0, ym, x1, y1, x0, y1));
			}
			case HALF_LEFT -> {
				quads.add(bakeTriangle(side, tex, x0, y0, xm, y0, xm, y1));
				quads.add(bakeTriangle(side, tex, x0, y0, xm, y1, x0, y1));
			}
			case HALF_RIGHT -> {
				quads.add(bakeTriangle(side, tex, xm, y0, x1, y0, x1, y1));
				quads.add(bakeTriangle(side, tex, xm, y0, x1, y1, xm, y1));
			}
			case TRIANGLE_SW -> quads.add(bakeTriangle(side, tex, x0, y0, x1, y0, x0, y1));
			case TRIANGLE_SE -> quads.add(bakeTriangle(side, tex, x1, y0, x1, y1, x0, y0));
			case TRIANGLE_NW -> quads.add(bakeTriangle(side, tex, x0, y1, x0, y0, x1, y1));
			case TRIANGLE_NE -> quads.add(bakeTriangle(side, tex, x1, y1, x0, y1, x1, y0));
			default -> quads.add(bakeSide(side, tex));
		}

		return quads;
	}

	private ScreenPieceType resolvePiece(@Nullable BlockState state) {
		if (state != null && state.hasProperty(ScreenBlock.piece)) {
			return state.getValue(ScreenBlock.piece);
		}
		return defaultPiece;
	}

	private Direction resolveFacing(@Nullable BlockState state) {
		if (state != null && state.hasProperty(ScreenBlock.FACING)) {
			return state.getValue(ScreenBlock.FACING);
		}
		return Direction.NORTH;
	}

	private boolean shouldUseShapedFace(@Nullable BlockState state, @Nullable Direction face) {
		if (face == null) {
			return false;
		}

		ScreenPieceType piece = resolvePiece(state);
		if (piece == ScreenPieceType.FULL) {
			return false;
		}

		return face == resolveFacing(state);
	}
	
	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
		return getQuads(state, side, random, ModelData.EMPTY, null);
	}
	
	@Override
	public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
		if (side == null)
			return noQuads;
		
		List<BakedQuad> ret = new ArrayList<>();
		
		int sid = BlockSide.reverse(side.ordinal());
		BlockSide s = blockSides[sid];
		TextureAtlasSprite tex = texs[15];
		if (data.has(TEXTURES[side.ordinal()]))
			tex = texs[data.get(TEXTURES[side.ordinal()])];
		if (shouldUseShapedFace(state, side)) {
			ret.addAll(bakeShapedSide(s, tex, resolvePiece(state)));
		} else {
			ret.add(bakeSide(s, tex));
		}
		return ret;
	}
	
	protected byte check(BlockState state, BlockAndTintGetter level, BlockPos pos, Vector3i dir) {
		BlockState u = level.getBlockState(pos.offset(dir.x, dir.y, dir.z));
		BlockState d = level.getBlockState(pos.offset(-dir.x, -dir.y, -dir.z));
		if (
				u.getBlock() == state.getBlock() &&
						d.getBlock() != state.getBlock()
		) return (byte) 1; // away
		else if (
				d.getBlock() == state.getBlock() &&
						u.getBlock() != state.getBlock()
		) return (byte) 2; // to
		else if (
				d.getBlock() != state.getBlock() &&
						u.getBlock() != state.getBlock()
		) return (byte) 3; // both
		return (byte) 0; // none
	}
	
	@Override
	public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
		ModelData.Builder builder = ModelData.builder();
		
		final int BAR_BOTTOM = 1;
		final int BAR_RIGHT = 2;
		final int BAR_TOP = 4;
		final int BAR_LEFT = 8;
		
		for (int i = 0; i < TEXTURES.length; i++) {
			BlockSide side = blockSides[i];
			
			// check up and down
			int res = switch (check(state, level, pos, side.up)) {
				case 1 -> BAR_BOTTOM;
				case 2 -> BAR_TOP;
				case 3 -> BAR_TOP | BAR_BOTTOM;
				default -> 0;
			};
			// check left and right
			res |= switch (check(state, level, pos, side.right)) {
				case 1 -> BAR_LEFT;
				case 2 -> BAR_RIGHT;
				case 3 -> BAR_LEFT | BAR_RIGHT;
				default -> 0;
			};
			
			builder.with(TEXTURES[i], res);
		}
		
		return builder.build();
	}
	
	@Override
	public boolean useAmbientOcclusion() {
		return true;
	}
	
	@Override
	public boolean isGui3d() {
		return true;
	}
	
	@Override
	public boolean usesBlockLight() {
		return false;
	}
	
	@Override
	public boolean isCustomRenderer() {
		return false;
	}
	
	@Override
	@Nonnull
	public TextureAtlasSprite getParticleIcon() {
		return texs[15];
	}
	
	@Override
	@Nonnull
	public ItemTransforms getTransforms() {
		return ItemTransforms.NO_TRANSFORMS;
	}
	
	@Override
	@Nonnull
	public ItemOverrides getOverrides() {
		return ItemOverrides.EMPTY;
	}
	
	//@formatter:off
    public static final class IntegerModelProperty extends ModelProperty<Integer> {}
    //@formatter:on
}
