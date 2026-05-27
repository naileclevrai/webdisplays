/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.link.LinkedScreenGroup;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.math.Vector3f;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;
import net.montoyo.wd.client.renderers.ScreenModelLoader;
import net.montoyo.wd.registry.BlockRegistry;
import org.jetbrains.annotations.NotNull;

import static com.mojang.math.Axis.*;


public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
	public ScreenRenderer() {
	}

	public static class ScreenRendererProvider implements BlockEntityRendererProvider<ScreenBlockEntity> {
		@Override
		public @NotNull BlockEntityRenderer<ScreenBlockEntity> create(@NotNull Context arg) {
			return new ScreenRenderer();
		}
	}

	private final Vector3f mid = new Vector3f();
	private final Vector3i tmpi = new Vector3i();
	private final Vector3f tmpf = new Vector3f();
	private static float clampUv(float uv) {
		if (uv < 0.0f)
			return 0.0f;
		if (uv > 1.0f)
			return 1.0f;
		return uv;
	}

	private static void addVertex(BufferBuilder builder, PoseStack poseStack, float x, float y, float z, float u, float v) {
		builder.vertex(poseStack.last().pose(), x, y, z).uv(u, v).color(1.f, 1.f, 1.f, 1.f).endVertex();
	}

	@Override
	public boolean shouldRenderOffScreen(ScreenBlockEntity pBlockEntity) {
		// Force rendering even when the block entity is off-screen
		// This is necessary for large render distances
		return true;
	}

	@Override
	public int getViewDistance() {
		// Return a large view distance to ensure screens render far away
		// This is based on the configured load distance
		return (int) Math.sqrt(WebDisplays.INSTANCE.loadDistance2) + 64;
	}
	
	@Override
	public void render(ScreenBlockEntity te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!te.isLoaded())
			return;

		//Disable lighting
//		RenderSystem.enableTexture();
//      RenderSystem.disableCull();
		RenderSystem.disableBlend();
		
		for (int i = 0; i < te.screenCount(); i++) {
			ScreenData scr = te.getScreen(i);
			if (scr.browser == null) {
				if (scr.isLinked() && WebDisplays.PROXY instanceof ClientProxy proxy) {
					LinkedScreenGroup group = proxy.getLinkedGroup(te, scr);
					if (group != null) {
						LinkedScreenGroup.Entry origin = group.getOrigin();
						if (origin != null && origin.screen == scr) {
							double dist = WebDisplays.PROXY.distanceTo(te, Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition());
							if (dist <= WebDisplays.INSTANCE.loadDistance2 * 16)
								scr.createBrowser(te, true);
							else
								continue;
						} else if (origin != null && origin.screen != null && origin.screen.browser != null) {
							scr.browser = origin.screen.browser;
						} else {
							continue;
						}
					} else {
						if (scr.linkOrigin) {
							double dist = WebDisplays.PROXY.distanceTo(te, Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition());
							if (dist <= WebDisplays.INSTANCE.loadDistance2 * 16)
								scr.createBrowser(te, true);
							else
								continue;
						} else {
							continue;
						}
					}
				} else {
					double dist = WebDisplays.PROXY.distanceTo(te, Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition());
					if (dist <= WebDisplays.INSTANCE.loadDistance2 * 16)
						scr.createBrowser(te, true);
					else continue;
				}
			}

			// TODO: manually backface cull the screens
			Vector3i shapeOrigin = new Vector3i(te.getBlockPos());
			if (CommonConfig.Screen.keepShapeOnChange) {
				shapeOrigin = ScreenShape.findConnectedOrigin(te.getLevel(), te.getBlockPos(), scr.side, CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY);
			}

			tmpi.set(scr.side.right);
			tmpi.mul(scr.size.x);
			tmpi.addMul(scr.side.up, scr.size.y);
			tmpf.set(tmpi);
			mid.set(0.5, 0.5, 0.5);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.left);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.down);
			mid.addMul(tmpf, 0.5f);
			tmpi.set(shapeOrigin);
			tmpi.sub(te.getBlockPos().getX(), te.getBlockPos().getY(), te.getBlockPos().getZ());
			tmpf.set(tmpi);
			mid.add(tmpf);

			double offsetPixels = ClientConfig.ScreenOffset.pixels;
			double offsetDistance = ClientConfig.ScreenOffset.distance;
			if (offsetPixels > 0.0) {
				Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
				double centerX = te.getBlockPos().getX() + mid.x;
				double centerY = te.getBlockPos().getY() + mid.y;
				double centerZ = te.getBlockPos().getZ() + mid.z;
				double dist2 = camPos.distanceToSqr(centerX, centerY, centerZ);
				if (offsetDistance <= 0.0 || dist2 >= offsetDistance * offsetDistance) {
					tmpf.set(scr.side.forward);
					mid.addMul(tmpf, (float) (offsetPixels / 16.0));
				}
			}
			
			poseStack.pushPose();
			poseStack.translate(mid.x, mid.y, mid.z);
			
			switch (scr.side) {
				case BOTTOM:
					poseStack.mulPose(XP.rotation(90.f + 49.8f));
					break;
				
				case TOP:
					poseStack.mulPose(XN.rotation(90.f + 49.8f));
					break;
				
				case NORTH:
					poseStack.mulPose(YN.rotationDegrees(180.f));
					break;
				
				case SOUTH:
					break;
				
				case WEST:
					poseStack.mulPose(YN.rotationDegrees(90.f));
					break;
				
				case EAST:
					poseStack.mulPose(YP.rotationDegrees(90.f));
					break;
			}
			
			if (scr.doTurnOnAnim) {
				long lt = System.currentTimeMillis() - scr.turnOnTime;
				float ft = ((float) lt) / 100.0f;
				
				if (ft >= 1.0f) {
					ft = 1.0f;
					scr.doTurnOnAnim = false;
				}
				
				poseStack.scale(ft, ft, 1.0f);
			}
			
			if (!scr.rotation.isNull)
				poseStack.mulPose(ZP.rotationDegrees(scr.rotation.angle));
			
			float sw = ((float) scr.size.x) * 0.5f - 2.f / 16.f;
			float sh = ((float) scr.size.y) * 0.5f - 2.f / 16.f;
			
			if (scr.rotation.isVertical) {
				float tmp = sw;
				sw = sh;
				sh = tmp;
			}
			
			ScreenShape.Data shape = ScreenShape.compute(te.getLevel(), shapeOrigin.toBlock(), scr.side, scr.size, scr.shapeMode, te.getBlockPos());
			float unitX = (sw * 2.0f) / scr.size.x;
			float unitY = (sh * 2.0f) / scr.size.y;
			LinkedScreenGroup group = null;
			LinkedScreenGroup.Entry groupEntry = null;
			Vector3i groupOffset = new Vector3i();
			Vector3i groupSize = new Vector3i(scr.size.x, scr.size.y, 0);
			if (scr.isLinked() && WebDisplays.PROXY instanceof ClientProxy proxy) {
				group = proxy.getLinkedGroup(te, scr);
				if (group != null) {
					groupEntry = group.getEntry(scr);
					Vector2i linkedSize = group.getSize();
					if (linkedSize != null && linkedSize.x > 0 && linkedSize.y > 0) {
						groupSize.set(linkedSize.x, linkedSize.y, 0);
					}
				}
			}
			if (groupEntry != null)
				groupOffset.set(groupEntry.offset.x, groupEntry.offset.y, 0);
			float invWidth = 1.0f / Math.max(1, groupSize.x);
			float invHeight = 1.0f / Math.max(1, groupSize.y);

			Tesselator tesselator = Tesselator.getInstance();
			BufferBuilder builder = tesselator.getBuilder();
			//TODO: don't use tesselator
			RenderSystem.enableDepthTest();

			if (scr.shapeMode != null && scr.shapeMode != ScreenShapeMode.NONE) {
				TextureAtlasSprite frame = Minecraft.getInstance()
					.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
					.apply(ScreenModelLoader.MATERIALS_SIDES[15].texture());
				float swFull = ((float) scr.size.x) * 0.5f;
				float shFull = ((float) scr.size.y) * 0.5f;
				float unitXFull = (swFull * 2.0f) / scr.size.x;
				float unitYFull = (shFull * 2.0f) / scr.size.y;
				float u0 = frame.getU(0.0f);
				float u1 = frame.getU(16.0f);

				RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
				RenderSystem._setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
				RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
				if (scr.shapeMode == ScreenShapeMode.SMOOTH_ONE && shape.smoothMask != null) {
					float frameZ = 0.49f;
					builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
					for (int y = 0; y < scr.size.y; y++) {
						for (int x = 0; x < scr.size.x; x++) {
							byte tri = shape.smoothMask[y * scr.size.x + x];
							if (tri == ScreenShape.SMOOTH_NONE)
								continue;

							float lx0 = -swFull + unitXFull * x;
							float lx1 = lx0 + unitXFull;
							float ly0 = -shFull + unitYFull * y;
							float ly1 = ly0 + unitYFull;

							switch (tri) {
								case ScreenShape.SMOOTH_BOTTOM_LEFT -> {
									addVertex(builder, poseStack, lx0, ly0, frameZ, frame.getU(0.0f), frame.getV(16.0f));
									addVertex(builder, poseStack, lx1, ly0, frameZ, frame.getU(16.0f), frame.getV(16.0f));
									addVertex(builder, poseStack, lx0, ly1, frameZ, frame.getU(0.0f), frame.getV(0.0f));
								}
								case ScreenShape.SMOOTH_TOP_LEFT -> {
									addVertex(builder, poseStack, lx0, ly1, frameZ, frame.getU(0.0f), frame.getV(0.0f));
									addVertex(builder, poseStack, lx0, ly0, frameZ, frame.getU(0.0f), frame.getV(16.0f));
									addVertex(builder, poseStack, lx1, ly1, frameZ, frame.getU(16.0f), frame.getV(0.0f));
								}
								case ScreenShape.SMOOTH_BOTTOM_RIGHT -> {
									addVertex(builder, poseStack, lx1, ly0, frameZ, frame.getU(16.0f), frame.getV(16.0f));
									addVertex(builder, poseStack, lx1, ly1, frameZ, frame.getU(16.0f), frame.getV(0.0f));
									addVertex(builder, poseStack, lx0, ly0, frameZ, frame.getU(0.0f), frame.getV(16.0f));
								}
								case ScreenShape.SMOOTH_TOP_RIGHT -> {
									addVertex(builder, poseStack, lx1, ly1, frameZ, frame.getU(16.0f), frame.getV(0.0f));
									addVertex(builder, poseStack, lx0, ly1, frameZ, frame.getU(0.0f), frame.getV(0.0f));
									addVertex(builder, poseStack, lx1, ly0, frameZ, frame.getU(16.0f), frame.getV(16.0f));
								}
							}
						}
					}
					tesselator.end();
				} else if (shape.topLeft != null) {
					builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

					for (int x = 0; x < scr.size.x; x++) {
						int colHeight = shape.columnHeights[x];
						if (colHeight <= 0)
							continue;

						float topLeft = shape.topLeft[x];
						float topRight = shape.topRight[x];
						float maxTop = Math.max(topLeft, topRight);
						if (maxTop <= colHeight)
							continue;

						float y0 = colHeight;
						float yLeft = Math.max(topLeft, colHeight);
						float yRight = Math.max(topRight, colHeight);
						float x0 = x;
						float x1 = x + 1;

						float lx0 = -swFull + unitXFull * x0;
						float lx1 = -swFull + unitXFull * x1;
						float ly0 = -shFull + unitYFull * y0;
						float lyLeft = -shFull + unitYFull * yLeft;
						float lyRight = -shFull + unitYFull * yRight;

						float v0 = frame.getV(16.0f);
						float localLeft = yLeft - (float) Math.floor(yLeft);
						if (localLeft == 0.0f)
							localLeft = 1.0f;
						float vLeft = frame.getV((1.0f - localLeft) * 16.0f);
						float localRight = yRight - (float) Math.floor(yRight);
						if (localRight == 0.0f)
							localRight = 1.0f;
						float vRight = frame.getV((1.0f - localRight) * 16.0f);

						builder.vertex(poseStack.last().pose(), lx0, ly0, 0.504f).uv(u0, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx1, ly0, 0.504f).uv(u1, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx1, lyRight, 0.504f).uv(u1, vRight).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx0, lyLeft, 0.504f).uv(u0, vLeft).color(1.f, 1.f, 1.f, 1.f).endVertex();
					}

					if (shape.bottomLeft != null && shape.bottomRight != null && shape.columnBottoms != null) {
						for (int x = 0; x < scr.size.x; x++) {
							int colBottom = shape.columnBottoms[x];
							if (colBottom < 0)
								continue;

							float bottomLeft = shape.bottomLeft[x];
							float bottomRight = shape.bottomRight[x];
							float minBottom = Math.min(bottomLeft, bottomRight);
							if (minBottom >= colBottom)
								continue;

							float yTop = colBottom;
							float yLeft = Math.min(bottomLeft, colBottom);
							float yRight = Math.min(bottomRight, colBottom);
							float x0 = x;
							float x1 = x + 1;

							float lx0 = -swFull + unitXFull * x0;
							float lx1 = -swFull + unitXFull * x1;
							float lyTop = -shFull + unitYFull * yTop;
							float lyLeft = -shFull + unitYFull * yLeft;
							float lyRight = -shFull + unitYFull * yRight;

							float vTop = frame.getV(0.0f);
							float localLeft = yLeft - (float) Math.floor(yLeft);
							float vLeft = frame.getV((1.0f - localLeft) * 16.0f);
							float localRight = yRight - (float) Math.floor(yRight);
							float vRight = frame.getV((1.0f - localRight) * 16.0f);

							builder.vertex(poseStack.last().pose(), lx0, lyLeft, 0.504f).uv(u0, vLeft).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx1, lyRight, 0.504f).uv(u1, vRight).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx1, lyTop, 0.504f).uv(u1, vTop).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx0, lyTop, 0.504f).uv(u0, vTop).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}

					if (shape.leftBottom != null && shape.leftTop != null && shape.rowLefts != null) {
						for (int y = 0; y < scr.size.y; y++) {
							int rowLeft = shape.rowLefts[y];
							if (rowLeft < 0)
								continue;

							float leftBottom = shape.leftBottom[y];
							float leftTop = shape.leftTop[y];
							float minLeft = Math.min(leftBottom, leftTop);
							if (minLeft >= rowLeft)
								continue;

							float xRight = rowLeft;
							float xBottom = Math.min(leftBottom, rowLeft);
							float xTop = Math.min(leftTop, rowLeft);
							float y0 = y;
							float y1 = y + 1;

							float lxRight = -swFull + unitXFull * xRight;
							float lxBottom = -swFull + unitXFull * xBottom;
							float lxTop = -swFull + unitXFull * xTop;
							float ly0 = -shFull + unitYFull * y0;
							float ly1 = -shFull + unitYFull * y1;

							float v0 = frame.getV(16.0f);
							float v1 = frame.getV(0.0f);
							float uRight = frame.getU(16.0f);
							float localBottom = xBottom - (float) Math.floor(xBottom);
							if (localBottom == 0.0f)
								localBottom = 1.0f;
							float uBottom = frame.getU((1.0f - localBottom) * 16.0f);
							float localTop = xTop - (float) Math.floor(xTop);
							if (localTop == 0.0f)
								localTop = 1.0f;
							float uTop = frame.getU((1.0f - localTop) * 16.0f);

							builder.vertex(poseStack.last().pose(), lxBottom, ly0, 0.504f).uv(uBottom, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxRight, ly0, 0.504f).uv(uRight, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxRight, ly1, 0.504f).uv(uRight, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxTop, ly1, 0.504f).uv(uTop, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}

					if (shape.rightBottom != null && shape.rightTop != null && shape.rowRights != null) {
						for (int y = 0; y < scr.size.y; y++) {
							int rowRight = shape.rowRights[y];
							if (rowRight < 0)
								continue;

							float rightBottom = shape.rightBottom[y];
							float rightTop = shape.rightTop[y];
							float maxRight = Math.max(rightBottom, rightTop);
							if (maxRight <= rowRight)
								continue;

							float xLeft = rowRight;
							float xBottom = Math.max(rightBottom, rowRight);
							float xTop = Math.max(rightTop, rowRight);
							float y0 = y;
							float y1 = y + 1;

							float lxLeft = -swFull + unitXFull * xLeft;
							float lxBottom = -swFull + unitXFull * xBottom;
							float lxTop = -swFull + unitXFull * xTop;
							float ly0 = -shFull + unitYFull * y0;
							float ly1 = -shFull + unitYFull * y1;

							float v0 = frame.getV(16.0f);
							float v1 = frame.getV(0.0f);
							float uLeft = frame.getU(0.0f);
							float localBottom = xBottom - (float) Math.floor(xBottom);
							if (localBottom == 0.0f)
								localBottom = 1.0f;
							float uBottom = frame.getU(localBottom * 16.0f);
							float localTop = xTop - (float) Math.floor(xTop);
							if (localTop == 0.0f)
								localTop = 1.0f;
							float uTop = frame.getU(localTop * 16.0f);

							builder.vertex(poseStack.last().pose(), lxLeft, ly0, 0.504f).uv(uLeft, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxBottom, ly0, 0.504f).uv(uBottom, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxTop, ly1, 0.504f).uv(uTop, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxLeft, ly1, 0.504f).uv(uLeft, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}

					tesselator.end();
				}
			}

			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
			RenderSystem._setShaderTexture(0, ((MCEFBrowser) scr.browser).getRenderer().getTextureID());
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int y = 0; y < scr.size.y; y++) {
				for (int x = 0; x < scr.size.x; x++) {
					if (!shape.hasBlock(x, y))
						continue;

					float x0 = -sw + unitX * x;
					float x1 = x0 + unitX;
					float y0 = -sh + unitY * y;
					float y1 = y0 + unitY;
					float u0 = (groupOffset.x + x) * invWidth;
					float u1 = (groupOffset.x + x + 1) * invWidth;
					float v0 = 1.0f - (groupOffset.y + y + 1) * invHeight;
					float v1 = 1.0f - (groupOffset.y + y) * invHeight;

					builder.vertex(poseStack.last().pose(), x0, y0, 0.505f).uv(u0, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
					builder.vertex(poseStack.last().pose(), x1, y0, 0.505f).uv(u1, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
					builder.vertex(poseStack.last().pose(), x1, y1, 0.505f).uv(u1, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
					builder.vertex(poseStack.last().pose(), x0, y1, 0.505f).uv(u0, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
				}
			}
			tesselator.end();

			if (scr.shapeMode != null && scr.shapeMode != ScreenShapeMode.NONE) {
				if (scr.shapeMode == ScreenShapeMode.SMOOTH_ONE && shape.smoothMask != null) {
					builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
					for (int y = 0; y < scr.size.y; y++) {
						for (int x = 0; x < scr.size.x; x++) {
							byte tri = shape.smoothMask[y * scr.size.x + x];
							if (tri == ScreenShape.SMOOTH_NONE)
								continue;

							float lx0 = -sw + unitX * x;
							float lx1 = lx0 + unitX;
							float ly0 = -sh + unitY * y;
							float ly1 = ly0 + unitY;

							float u00 = (groupOffset.x + x) * invWidth;
							float u10 = (groupOffset.x + x + 1) * invWidth;
							float v00 = 1.0f - (groupOffset.y + y) * invHeight;
							float v01 = 1.0f - (groupOffset.y + y + 1) * invHeight;

							switch (tri) {
								case ScreenShape.SMOOTH_BOTTOM_LEFT -> {
									addVertex(builder, poseStack, lx0, ly0, 0.505f, u00, v00);
									addVertex(builder, poseStack, lx1, ly0, 0.505f, u10, v00);
									addVertex(builder, poseStack, lx0, ly1, 0.505f, u00, v01);
								}
								case ScreenShape.SMOOTH_TOP_LEFT -> {
									addVertex(builder, poseStack, lx0, ly1, 0.505f, u00, v01);
									addVertex(builder, poseStack, lx0, ly0, 0.505f, u00, v00);
									addVertex(builder, poseStack, lx1, ly1, 0.505f, u10, v01);
								}
								case ScreenShape.SMOOTH_BOTTOM_RIGHT -> {
									addVertex(builder, poseStack, lx1, ly0, 0.505f, u10, v00);
									addVertex(builder, poseStack, lx1, ly1, 0.505f, u10, v01);
									addVertex(builder, poseStack, lx0, ly0, 0.505f, u00, v00);
								}
								case ScreenShape.SMOOTH_TOP_RIGHT -> {
									addVertex(builder, poseStack, lx1, ly1, 0.505f, u10, v01);
									addVertex(builder, poseStack, lx0, ly1, 0.505f, u00, v01);
									addVertex(builder, poseStack, lx1, ly0, 0.505f, u10, v00);
								}
							}
						}
					}
					tesselator.end();
				} else if (shape.topLeft != null) {
					builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
					for (int x = 0; x < scr.size.x; x++) {
						int colHeight = shape.columnHeights[x];
						if (colHeight <= 0)
							continue;

						float topLeft = shape.topLeft[x];
						float topRight = shape.topRight[x];
						float maxTop = Math.max(topLeft, topRight);
						if (maxTop <= colHeight)
							continue;

						float y0 = colHeight;
						float yLeft = Math.max(topLeft, colHeight);
						float yRight = Math.max(topRight, colHeight);
						float x0 = x;
						float x1 = x + 1;

						float lx0 = -sw + unitX * x0;
						float lx1 = -sw + unitX * x1;
						float ly0 = -sh + unitY * y0;
						float lyLeft = -sh + unitY * yLeft;
						float lyRight = -sh + unitY * yRight;

						float u0 = (groupOffset.x + x0) * invWidth;
						float u1 = (groupOffset.x + x1) * invWidth;
						float v0 = 1.0f - (groupOffset.y + y0) * invHeight;
						float vLeft = 1.0f - (groupOffset.y + yLeft) * invHeight;
						float vRight = 1.0f - (groupOffset.y + yRight) * invHeight;

						builder.vertex(poseStack.last().pose(), lx0, ly0, 0.505f).uv(u0, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx1, ly0, 0.505f).uv(u1, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx1, lyRight, 0.505f).uv(u1, vRight).color(1.f, 1.f, 1.f, 1.f).endVertex();
						builder.vertex(poseStack.last().pose(), lx0, lyLeft, 0.505f).uv(u0, vLeft).color(1.f, 1.f, 1.f, 1.f).endVertex();
					}

					if (shape.bottomLeft != null && shape.bottomRight != null && shape.columnBottoms != null) {
						for (int x = 0; x < scr.size.x; x++) {
							int colBottom = shape.columnBottoms[x];
							if (colBottom < 0)
								continue;

							float bottomLeft = shape.bottomLeft[x];
							float bottomRight = shape.bottomRight[x];
							float minBottom = Math.min(bottomLeft, bottomRight);
							if (minBottom >= colBottom)
								continue;

							float yTop = colBottom;
							float yLeft = Math.min(bottomLeft, colBottom);
							float yRight = Math.min(bottomRight, colBottom);
							float x0 = x;
							float x1 = x + 1;

							float lx0 = -sw + unitX * x0;
							float lx1 = -sw + unitX * x1;
							float lyTop = -sh + unitY * yTop;
							float lyLeft = -sh + unitY * yLeft;
							float lyRight = -sh + unitY * yRight;

							float u0 = (groupOffset.x + x0) * invWidth;
							float u1 = (groupOffset.x + x1) * invWidth;
							float vTop = 1.0f - (groupOffset.y + yTop) * invHeight;
							float vLeft = 1.0f - (groupOffset.y + yLeft) * invHeight;
							float vRight = 1.0f - (groupOffset.y + yRight) * invHeight;

							builder.vertex(poseStack.last().pose(), lx0, lyLeft, 0.505f).uv(u0, vLeft).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx1, lyRight, 0.505f).uv(u1, vRight).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx1, lyTop, 0.505f).uv(u1, vTop).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lx0, lyTop, 0.505f).uv(u0, vTop).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}

					if (shape.leftBottom != null && shape.leftTop != null && shape.rowLefts != null) {
						for (int y = 0; y < scr.size.y; y++) {
							int rowLeft = shape.rowLefts[y];
							if (rowLeft < 0)
								continue;

							float leftBottom = shape.leftBottom[y];
							float leftTop = shape.leftTop[y];
							float minLeft = Math.min(leftBottom, leftTop);
							if (minLeft >= rowLeft)
								continue;

							float xRight = rowLeft;
							float xBottom = Math.min(leftBottom, rowLeft);
							float xTop = Math.min(leftTop, rowLeft);
							float y0 = y;
							float y1 = y + 1;

							float lxRight = -sw + unitX * xRight;
							float lxBottom = -sw + unitX * xBottom;
							float lxTop = -sw + unitX * xTop;
							float ly0 = -sh + unitY * y0;
							float ly1 = -sh + unitY * y1;

							float uRight = (groupOffset.x + xRight) * invWidth;
							float uBottom = (groupOffset.x + xBottom) * invWidth;
							float uTop = (groupOffset.x + xTop) * invWidth;
							float v0 = 1.0f - (groupOffset.y + y + 1) * invHeight;
							float v1 = 1.0f - (groupOffset.y + y) * invHeight;

							builder.vertex(poseStack.last().pose(), lxBottom, ly0, 0.505f).uv(uBottom, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxRight, ly0, 0.505f).uv(uRight, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxRight, ly1, 0.505f).uv(uRight, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxTop, ly1, 0.505f).uv(uTop, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}

					if (shape.rightBottom != null && shape.rightTop != null && shape.rowRights != null) {
						for (int y = 0; y < scr.size.y; y++) {
							int rowRight = shape.rowRights[y];
							if (rowRight < 0)
								continue;

							float rightBottom = shape.rightBottom[y];
							float rightTop = shape.rightTop[y];
							float maxRight = Math.max(rightBottom, rightTop);
							if (maxRight <= rowRight)
								continue;

							float xLeft = rowRight;
							float xBottom = Math.max(rightBottom, rowRight);
							float xTop = Math.max(rightTop, rowRight);
							float y0 = y;
							float y1 = y + 1;

							float lxLeft = -sw + unitX * xLeft;
							float lxBottom = -sw + unitX * xBottom;
							float lxTop = -sw + unitX * xTop;
							float ly0 = -sh + unitY * y0;
							float ly1 = -sh + unitY * y1;

							float uLeft = (groupOffset.x + xLeft) * invWidth;
							float uBottom = (groupOffset.x + xBottom) * invWidth;
							float uTop = (groupOffset.x + xTop) * invWidth;
							float v0 = 1.0f - (groupOffset.y + y + 1) * invHeight;
							float v1 = 1.0f - (groupOffset.y + y) * invHeight;

							builder.vertex(poseStack.last().pose(), lxLeft, ly0, 0.505f).uv(uLeft, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxBottom, ly0, 0.505f).uv(uBottom, v0).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxTop, ly1, 0.505f).uv(uTop, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
							builder.vertex(poseStack.last().pose(), lxLeft, ly1, 0.505f).uv(uLeft, v1).color(1.f, 1.f, 1.f, 1.f).endVertex();
						}
					}
					tesselator.end();
				}
			}
			RenderSystem.disableDepthTest();
			
			// TODO: it'd be neat to draw a mouse cursor on the screen
//			// debug hit2pixels
//			HitResult result = Minecraft.getInstance().hitResult;
//			VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
//			poseStack.translate(-sw, -sh, 0);
//			if (result instanceof BlockHitResult hit) {
//				BlockPos bpos = hit.getBlockPos();
//
//				Vector3i pos = new Vector3i(hit.getBlockPos());
//				float hitX = ((float) result.getLocation().x) - (float) te.getBlockPos().getX();
//				float hitY = ((float) result.getLocation().y) - (float) te.getBlockPos().getY();
//				float hitZ = ((float) result.getLocation().z) - (float) te.getBlockPos().getZ();
//				Vector2i tmp = new Vector2i();
//
//				if (BlockScreen.hit2pixels(scr.side, bpos, pos, scr, hitX, hitY, hitZ, tmp)) {
//					float x = tmp.x / (float) scr.resolution.x * scr.size.x;
//					float y = tmp.y / (float) scr.resolution.y * scr.size.y;
//					y = scr.size.y - y;
//
//					x /= scr.size.x;
//					y /= scr.size.y;
//					x *= sw * 2;
//					y *= sh * 2;
//
//					LevelRenderer.renderLineBox(
//							poseStack,
//							consumer, new AABB(
//									x - 0.01, y - 0.01, 0.5 - 0.01,
//									x + 0.01, y + 0.01, 0.5 + 0.01
//							),
//							1f, 0, 0, 1f
//					);
//				}
//			}
			
			poseStack.popPose();
		}


//        //Bounding box debugging
//        poseStack.pushPose();
//        poseStack.translate(-te.getBlockPos().getX(), -te.getBlockPos().getY(), -te.getBlockPos().getZ());
//        LevelRenderer.renderLineBox(
//                poseStack, bufferSource.getBuffer(RenderType.LINES),
//                te.getRenderBoundingBox(), 1, 1, 1, 1f
//        );
//        poseStack.popPose();
		
		//Re-enable lighting
//        RenderSystem.enableCull();
	}

}
