package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.montoyo.wd.client.link.LinkedScreenGroup;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.link.DiagonalCornerHelper;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3f;
import net.montoyo.wd.utilities.math.Vector3i;

import static com.mojang.math.Axis.*;
import static net.montoyo.wd.client.renderers.ScreenRenderer.renderScreenCell;

/**
 * Rendu unifié d'une chaîne diagonale : un seul browser, une colonne = une face de bloc (pas de plan géant).
 */
public final class DiagonalScreenRenderer {
    private static final Vector3f mid = new Vector3f();
    private static final Vector3i tmpi = new Vector3i();
    private static final Vector3f tmpf = new Vector3f();
    private static final float[] diagUv = new float[4];

    private DiagonalScreenRenderer() {
    }

    public static void renderGroup(ScreenBlockEntity originTe, ScreenData originScr, LinkedScreenGroup group,
                                   PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                   boolean showTestPattern) {
        Vector2i groupSize = group.getSize();
        if (groupSize == null || groupSize.x < 1 || groupSize.y < 1)
            return;

        BlockPos originPos = originTe.getBlockPos();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        RenderSystem.enableDepthTest();

        if (showTestPattern) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            for (LinkedScreenGroup.Entry entry : group.getEntriesSorted()) {
                drawColumnPattern(originTe, originPos, entry, groupSize, poseStack, tesselator, builder,
                        bufferSource, Minecraft.getInstance().font, packedLight, originScr, false);
            }
            LinkedScreenGroup.Entry originEntry = group.getEntry(originScr);
            if (originEntry != null) {
                drawColumnPattern(originTe, originPos, originEntry, groupSize, poseStack, tesselator, builder,
                        bufferSource, Minecraft.getInstance().font, packedLight, originScr, true);
            }
        } else if (originScr.browser instanceof MCEFBrowser browser) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem._setShaderTexture(0, browser.getRenderer().getTextureID());
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (LinkedScreenGroup.Entry entry : group.getEntriesSorted()) {
                appendColumnQuads(originTe, originPos, entry, groupSize, poseStack, builder);
            }
            tesselator.end();
        }

        RenderSystem.disableDepthTest();
    }

    private static void appendColumnQuads(ScreenBlockEntity originTe, BlockPos originPos, LinkedScreenGroup.Entry entry,
                                          Vector2i groupSize, PoseStack poseStack, BufferBuilder builder) {
        ScreenBlockEntity columnTe = entry.blockEntity;
        ScreenData columnScr = entry.screen;
        BlockSide side = columnScr.side;

        poseStack.pushPose();
        applyColumnTransform(originTe, originPos, columnTe, columnScr, side, poseStack);

        float sw = columnScr.size.x * 0.5f;
        float sh = columnScr.size.y * 0.5f;
        if (columnScr.rotation.isVertical) {
            float tmp = sw;
            sw = sh;
            sh = tmp;
        }

        Vector3i shapeOrigin = shapeOrigin(columnTe, columnScr);
        ScreenShape.Data shape = ScreenShape.compute(columnTe.getLevel(), shapeOrigin.toBlock(), side,
                columnScr.size, columnScr.shapeMode, columnTe.getBlockPos());
        float unitX = (sw * 2.0f) / columnScr.size.x;
        float unitY = (sh * 2.0f) / columnScr.size.y;

        for (int y = 0; y < columnScr.size.y; y++) {
            for (int x = 0; x < columnScr.size.x; x++) {
                if (!shape.hasBlock(x, y))
                    continue;

                float x0 = -sw + unitX * x;
                float x1 = x0 + unitX;
                float y0 = -sh + unitY * y;
                float y1 = y0 + unitY;

                DiagonalCornerHelper.mapDiagonalCellUv(entry.chainIndex, columnScr.size.x, columnScr.size.y,
                        x, y, groupSize.x, groupSize.y, diagUv);

                renderScreenCell(builder, poseStack, shape.getPiece(x, y), 0.505f,
                        x0, x1, y0, y1, diagUv[0], diagUv[1], diagUv[2], diagUv[3]);
            }
        }
        poseStack.popPose();
    }

    private static void drawColumnPattern(ScreenBlockEntity originTe, BlockPos originPos, LinkedScreenGroup.Entry entry,
                                          Vector2i groupSize, PoseStack poseStack, Tesselator tesselator,
                                          BufferBuilder builder, MultiBufferSource bufferSource,
                                          net.minecraft.client.gui.Font font, int packedLight,
                                          ScreenData originScr, boolean labelsOnly) {
        ScreenBlockEntity columnTe = entry.blockEntity;
        ScreenData columnScr = entry.screen;
        BlockSide side = columnScr.side;

        poseStack.pushPose();
        applyColumnTransform(originTe, originPos, columnTe, columnScr, side, poseStack);

        float sw = columnScr.size.x * 0.5f;
        float sh = columnScr.size.y * 0.5f;
        if (columnScr.rotation.isVertical) {
            float tmp = sw;
            sw = sh;
            sh = tmp;
        }

        Vector3i shapeOrigin = shapeOrigin(columnTe, columnScr);
        ScreenShape.Data shape = ScreenShape.compute(columnTe.getLevel(), shapeOrigin.toBlock(), side,
                columnScr.size, columnScr.shapeMode, columnTe.getBlockPos());
        float unitX = (sw * 2.0f) / columnScr.size.x;
        float unitY = (sh * 2.0f) / columnScr.size.y;

        if (!labelsOnly) {
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            long time = System.currentTimeMillis();
            float scanY = (time % 4000L) / 4000f;
            float faceW = sw * 2f;
            float faceH = sh * 2f;
            for (int y = 0; y < columnScr.size.y; y++) {
                for (int x = 0; x < columnScr.size.x; x++) {
                    if (!shape.hasBlock(x, y))
                        continue;
                    float x0 = -sw + unitX * x;
                    float x1 = x0 + unitX;
                    float y0 = -sh + unitY * y;
                    float y1 = y0 + unitY;
                    ScreenTestPatternRenderer.renderDiagonalCellPattern(builder, poseStack, shape.getPiece(x, y),
                            0.505f, x0, x1, y0, y1, sw, sh, faceW, faceH, scanY, entry, groupSize, x, y);
                }
            }
            tesselator.end();
        } else {
            ScreenTestPatternRenderer.renderDiagonalLabels(poseStack, bufferSource, font, packedLight, originScr, sw, sh);
        }
        poseStack.popPose();
    }

    private static void applyColumnTransform(ScreenBlockEntity originTe, BlockPos originPos,
                                             ScreenBlockEntity columnTe, ScreenData columnScr, BlockSide side,
                                             PoseStack poseStack) {
        BlockPos delta = columnTe.getBlockPos().subtract(originPos);
        poseStack.translate(delta.getX(), delta.getY(), delta.getZ());

        Vector3i shapeOrigin = shapeOrigin(columnTe, columnScr);
        tmpi.set(side.right);
        tmpi.mul(columnScr.size.x);
        tmpi.addMul(side.up, columnScr.size.y);
        tmpf.set(tmpi);
        mid.set(0.5f, 0.5f, 0.5f);
        mid.addMul(tmpf, 0.5f);
        tmpf.set(side.left);
        mid.addMul(tmpf, 0.5f);
        tmpf.set(side.down);
        mid.addMul(tmpf, 0.5f);
        tmpi.set(shapeOrigin);
        tmpi.sub(columnTe.getBlockPos().getX(), columnTe.getBlockPos().getY(), columnTe.getBlockPos().getZ());
        tmpf.set(tmpi);
        mid.add(tmpf);

        double offsetPixels = ClientConfig.ScreenOffset.pixels;
        double offsetDistance = ClientConfig.ScreenOffset.distance;
        if (offsetPixels > 0.0) {
            Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
            double centerX = columnTe.getBlockPos().getX() + mid.x;
            double centerY = columnTe.getBlockPos().getY() + mid.y;
            double centerZ = columnTe.getBlockPos().getZ() + mid.z;
            double dist2 = camPos.distanceToSqr(centerX, centerY, centerZ);
            if (offsetDistance <= 0.0 || dist2 >= offsetDistance * offsetDistance) {
                tmpf.set(side.forward);
                mid.addMul(tmpf, (float) (offsetPixels / 16.0));
            }
        }

        poseStack.translate(mid.x, mid.y, mid.z);

        switch (columnScr.side) {
            case BOTTOM -> poseStack.mulPose(XP.rotation(90.f + 49.8f));
            case TOP -> poseStack.mulPose(XN.rotation(90.f + 49.8f));
            case NORTH -> poseStack.mulPose(YN.rotationDegrees(180.f));
            case SOUTH -> { /* default */ }
            case WEST -> poseStack.mulPose(YN.rotationDegrees(90.f));
            case EAST -> poseStack.mulPose(YP.rotationDegrees(90.f));
        }

        if (columnScr.doTurnOnAnim) {
            long lt = System.currentTimeMillis() - columnScr.turnOnTime;
            float ft = Math.min(1.0f, ((float) lt) / 100.0f);
            if (ft >= 1.0f)
                columnScr.doTurnOnAnim = false;
            poseStack.scale(ft, ft, 1.0f);
        }
        if (!columnScr.rotation.isNull)
            poseStack.mulPose(ZP.rotationDegrees(columnScr.rotation.angle));
    }

    private static Vector3i shapeOrigin(ScreenBlockEntity te, ScreenData scr) {
        Vector3i shapeOrigin = new Vector3i(te.getBlockPos());
        if (CommonConfig.Screen.keepShapeOnChange) {
            Vector3i found = ScreenShape.findConnectedOrigin(te.getLevel(), te.getBlockPos(), scr.side,
                    CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY);
            if (found != null)
                shapeOrigin = found;
        }
        return shapeOrigin;
    }
}
