package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FormattedCharSequence;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.data.ScreenPieceType;
import net.montoyo.wd.utilities.math.Vector2i;

/**
 * Procedural broadcast-style test pattern rendered directly on screen faces (no browser texture).
 */
public final class ScreenTestPatternRenderer {
    private static final float[] BAR_R = {1f, 1f, 0f, 0f, 0f, 1f, 1f};
    private static final float[] BAR_G = {0f, 1f, 1f, 1f, 0f, 0f, 1f};
    private static final float[] BAR_B = {0f, 0f, 0f, 1f, 1f, 1f, 1f};

    private ScreenTestPatternRenderer() {
    }

    public static void render(PoseStack poseStack, Tesselator tesselator, BufferBuilder builder,
                              ScreenShape.Data shape, ScreenData scr, float sw, float sh,
                              float unitX, float unitY, Vector2i groupOffset,
                              MultiBufferSource bufferSource, Font font, int packedLight) {
        long time = System.currentTimeMillis();
        float scanY = (time % 4000L) / 4000f;
        float faceW = sw * 2f;
        float faceH = sh * 2f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int y = 0; y < scr.size.y; y++) {
            for (int x = 0; x < scr.size.x; x++) {
                if (!shape.hasBlock(x, y))
                    continue;

                float x0 = -sw + unitX * x;
                float x1 = x0 + unitX;
                float y0 = -sh + unitY * y;
                float y1 = y0 + unitY;

                renderCellPattern(builder, poseStack, shape.getPiece(x, y), 0.505f,
                        x0, x1, y0, y1, sw, sh, faceW, faceH, scanY);
            }
        }
        tesselator.end();

        // Crosshair + circle overlay
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        drawCrosshair(builder, poseStack, 0.506f, sw, sh);
        tesselator.end();

        drawLabels(poseStack, bufferSource, font, packedLight, scr, sw, sh, groupOffset);
    }

    private static void renderCellPattern(BufferBuilder builder, PoseStack poseStack, ScreenPieceType piece,
                                          float z, float x0, float x1, float y0, float y1,
                                          float sw, float sh, float faceW, float faceH, float scanY) {
        int steps = 4;
        for (int sy = 0; sy < steps; sy++) {
            for (int sx = 0; sx < steps; sx++) {
                float lx0 = x0 + (x1 - x0) * sx / steps;
                float lx1 = x0 + (x1 - x0) * (sx + 1) / steps;
                float ly0 = y0 + (y1 - y0) * sy / steps;
                float ly1 = y0 + (y1 - y0) * (sy + 1) / steps;

                float nx = (lx0 + lx1) * 0.5f + sw;
                float ny = (ly0 + ly1) * 0.5f + sh;
                nx /= faceW;
                ny /= faceH;

                float[] rgb = samplePattern(nx, ny, scanY);
                addColorCell(builder, poseStack, piece, z, lx0, lx1, ly0, ly1, rgb[0], rgb[1], rgb[2]);
            }
        }
    }

    private static float[] samplePattern(float nx, float ny, float scanY) {
        float r, g, b;

        if (nx < 0.11f) {
            int band = (int) (ny * 7f) % 7;
            if (band < 0) band = 0;
            r = BAR_R[band];
            g = BAR_G[band];
            b = BAR_B[band];
        } else if (nx > 0.89f) {
            float gray = 1f - ny;
            r = g = b = gray;
        } else if (nx < 0.16f && ny > 0.84f) {
            // Top-left wedge (brightness ramp)
            float t = (nx / 0.16f + (1f - ny) / 0.16f) * 0.5f;
            r = g = b = t;
        } else {
            int gx = (int) (nx * 16f);
            int gy = (int) (ny * 9f);
            boolean dark = ((gx + gy) & 1) == 0;
            r = dark ? 0.12f : 0.22f;
            g = dark ? 0.12f : 0.22f;
            b = dark ? 0.14f : 0.24f;
        }

        if (Math.abs(ny - scanY) < 0.012f) {
            r = g = b = 1f;
        }

        return new float[]{r, g, b};
    }

    private static void drawCrosshair(BufferBuilder builder, PoseStack poseStack, float z, float sw, float sh) {
        float t = Math.min(sw, sh) * 0.004f;
        float cr = 0.85f, cg = 0.85f, cb = 0.85f;

        // Horizontal line
        addColorQuad(builder, poseStack, z, -sw, -t, sw, t, cr, cg, cb);
        // Vertical line
        addColorQuad(builder, poseStack, z, -t, -sh, t, sh, cr, cg, cb);

        // Circle approximation (octagon)
        float radius = Math.min(sw, sh) * 0.55f;
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2 * Math.PI * i / segments);
            float a1 = (float) (2 * Math.PI * (i + 1) / segments);
            float x0 = (float) Math.cos(a0) * radius;
            float y0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float y1 = (float) Math.sin(a1) * radius;
            builder.vertex(poseStack.last().pose(), 0, 0, z).color(cr, cg, cb, 1f).endVertex();
            builder.vertex(poseStack.last().pose(), x0, y0, z).color(cr, cg, cb, 1f).endVertex();
            builder.vertex(poseStack.last().pose(), x1, y1, z).color(cr, cg, cb, 1f).endVertex();
        }
    }

    private static void drawLabels(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int packedLight, ScreenData scr, float sw, float sh, Vector2i groupOffset) {
        String role;
        if (scr.isLinked()) {
            role = scr.linkOrigin
                    ? I18n.get("webdisplays.testpattern.role.master")
                    : I18n.get("webdisplays.testpattern.role.slave");
        } else {
            role = I18n.get("webdisplays.testpattern.role.screen");
        }

        String resolution = scr.resolution.x + " x " + scr.resolution.y;
        String blocks = I18n.get("webdisplays.testpattern.blocks", scr.size.x, scr.size.y);
        String offset = (scr.isLinked() && groupOffset != null)
                ? "@" + groupOffset.x + "," + groupOffset.y
                : null;

        poseStack.pushPose();
        float textScale = Math.min(sw, sh) * 0.045f;
        poseStack.translate(0, 0, 0.508f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(scr.rotation != null ? scr.rotation.angle : 0));
        poseStack.scale(textScale, -textScale, textScale);

        int y = -14;
        drawCenteredLine(font, poseStack, bufferSource, packedLight, role, y, 0xFFFFFFFF);
        y += 12;
        drawCenteredLine(font, poseStack, bufferSource, packedLight, resolution, y, 0xFFCCCCCC);
        y += 12;
        drawCenteredLine(font, poseStack, bufferSource, packedLight, blocks, y, 0xFFAAAAAA);
        if (offset != null) {
            y += 12;
            drawCenteredLine(font, poseStack, bufferSource, packedLight, offset, y, 0xFF888888);
        }

        poseStack.popPose();
    }

    private static void drawCenteredLine(Font font, PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, String text, int y, int color) {
        FormattedCharSequence seq = FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY);
        float w = font.width(seq);
        font.drawInBatch(seq, -w * 0.5f, y, color, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
    }

    private static void addColorCell(BufferBuilder builder, PoseStack poseStack, ScreenPieceType piece,
                                   float z, float x0, float x1, float y0, float y1,
                                   float r, float g, float b) {
        switch (piece) {
            case FULL -> {
                addColorQuad(builder, poseStack, z, x0, y0, x1, y1, r, g, b);
            }
            case HALF_BOTTOM -> {
                float ym = (y0 + y1) * 0.5f;
                addColorTriangle(builder, poseStack, z, x0, y0, x1, y0, x1, ym, r, g, b);
                addColorTriangle(builder, poseStack, z, x0, y0, x1, ym, x0, ym, r, g, b);
            }
            case HALF_TOP -> {
                float ym = (y0 + y1) * 0.5f;
                addColorTriangle(builder, poseStack, z, x0, ym, x1, ym, x1, y1, r, g, b);
                addColorTriangle(builder, poseStack, z, x0, ym, x1, y1, x0, y1, r, g, b);
            }
            case HALF_LEFT -> {
                float xm = (x0 + x1) * 0.5f;
                addColorTriangle(builder, poseStack, z, x0, y0, xm, y0, xm, y1, r, g, b);
                addColorTriangle(builder, poseStack, z, x0, y0, xm, y1, x0, y1, r, g, b);
            }
            case HALF_RIGHT -> {
                float xm = (x0 + x1) * 0.5f;
                addColorTriangle(builder, poseStack, z, xm, y0, x1, y0, x1, y1, r, g, b);
                addColorTriangle(builder, poseStack, z, xm, y0, x1, y1, xm, y1, r, g, b);
            }
            case TRIANGLE_SW -> addColorTriangle(builder, poseStack, z, x0, y0, x1, y0, x0, y1, r, g, b);
            case TRIANGLE_SE -> addColorTriangle(builder, poseStack, z, x1, y0, x1, y1, x0, y0, r, g, b);
            case TRIANGLE_NW -> addColorTriangle(builder, poseStack, z, x0, y1, x0, y0, x1, y1, r, g, b);
            case TRIANGLE_NE -> addColorTriangle(builder, poseStack, z, x1, y1, x0, y1, x1, y0, r, g, b);
        }
    }

    private static void addColorQuad(BufferBuilder builder, PoseStack poseStack, float z,
                                     float x0, float y0, float x1, float y1,
                                     float r, float g, float b) {
        addColorTriangle(builder, poseStack, z, x0, y0, x1, y0, x1, y1, r, g, b);
        addColorTriangle(builder, poseStack, z, x0, y0, x1, y1, x0, y1, r, g, b);
    }

    private static void addColorTriangle(BufferBuilder builder, PoseStack poseStack, float z,
                                       float x0, float y0, float x1, float y1, float x2, float y2,
                                       float r, float g, float b) {
        builder.vertex(poseStack.last().pose(), x0, y0, z).color(r, g, b, 1f).endVertex();
        builder.vertex(poseStack.last().pose(), x1, y1, z).color(r, g, b, 1f).endVertex();
        builder.vertex(poseStack.last().pose(), x2, y2, z).color(r, g, b, 1f).endVertex();
    }
}
