package ru.meetfroz.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import ru.meetfroz.client.GPSMarker;

import java.util.ArrayList;
import java.util.List;

/**
 * Версия-специфичный рендер GPS-меток для Minecraft 1.21.5–1.21.8 (эра RenderPipeline).
 *
 * Над точкой назначения рисуется компактное информационное окошко (billboard к
 * камере): полупрозрачный тёмный фон со скруглёнными углами, светящаяся рамка и
 * текст (название / расстояние / X Y Z). Видно сквозь стены: геометрия — через
 * кастомный RenderPipeline с NO_DEPTH_TEST, текст — TextLayerType.SEE_THROUGH.
 *
 * Панель строится в «пиксельном» пространстве и масштабируется по расстоянию,
 * поэтому текст остаётся читаемым на любой дальности.
 */
public final class RenderCompat {

    private static final int SEGMENTS = 44;

    private static final RenderPipeline SEE_THROUGH_PIPELINE = RenderPipeline
            .builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("meetfroz", "see_through"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .build();

    // Полупрозрачный слой (заливка/рамка/столб/кольцо) — без теста глубины и без culling.
    private static final RenderLayer SEE_THROUGH = RenderLayer.of(
            "meetfroz_see_through",
            65536,
            SEE_THROUGH_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false));

    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline
            .builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("meetfroz", "glow"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(false)
            .withDepthWrite(false)
            .build();

    // Аддитивный слой для внешнего свечения рамки.
    private static final RenderLayer GLOW = RenderLayer.of(
            "meetfroz_glow",
            32768,
            GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false));

    // ---- панель (пиксельные константы) ----
    // Адаптивный масштаб: чем дальше метка, тем крупнее панель в мире, чтобы
    // на экране текст оставался читаемым (ограничен снизу и сверху).
    private static final float PANEL_SCALE_MIN = 0.035f;   // блоков на пиксель (у метки)
    private static final float PANEL_SCALE_PER_BLOCK = 0.0018f; // рост масштаба с расстоянием
    private static final float PANEL_SCALE_MAX = 7.4f;      // потолок (панель не «гигантская»)
    private static final float LINE_HEIGHT = 11.0f;
    private static final float PAD_X = 7.0f;
    private static final float PAD_Y = 6.0f;
    private static final float CORNER_R = 4.0f;
    private static final int CORNER_SEG = 3;

    private RenderCompat() {
    }

    public static void drawMarker(WorldRenderContext ctx, GPSMarker m,
                                  double camX, double camY, double camZ,
                                  float alpha, float anim, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        Camera camera = ctx.camera();
        if (matrices == null || consumers == null || mc.player == null) return;

        int ir = (color >> 16) & 0xFF, ig = (color >> 8) & 0xFF, ib = color & 0xFF;

        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * Math.PI * 2.0); // 0..1
        float headY = 2.45f + 0.10f * pulse;

        matrices.push();
        matrices.translate(m.x - camX, m.y - camY, m.z - camZ);
        MatrixStack.Entry entry = matrices.peek();

        // Кольцо у точки + световой столб вверх к панели.
        drawRing(entry, consumers, ir, ig, ib, alpha, pulse);
        drawBeam(entry, consumers, ir, ig, ib, alpha, headY);

        // Информационное окошко над точкой (billboard к камере).
        // Масштаб адаптируется к расстоянию: издалека панель крупнее в мире,
        // поэтому на экране она (вместе с текстом) остаётся читаемой.
        double dist = Math.sqrt(m.squaredDistanceTo(camX, camY, camZ));
        float panelScale = (float) Math.min(PANEL_SCALE_MAX,
                Math.max(PANEL_SCALE_MIN, dist * PANEL_SCALE_PER_BLOCK));

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.translate(0f, headY + 0.45f, 0f);
        matrices.scale(-panelScale, -panelScale, panelScale); // масштаб px->блоки + разворот текста
        drawPanel(matrices.peek(), consumers, mc, m, alpha, color);
        matrices.pop();

        matrices.pop();
    }

    // ------------------------------------------------------------------ panel

    private static void drawPanel(MatrixStack.Entry entry, VertexConsumerProvider consumers,
                                  MinecraftClient mc, GPSMarker m, float alpha, int color) {
        TextRenderer tr = mc.textRenderer;

        double dist = m.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        String name = m.name;
        String distLine = Math.round(dist) + " м";
        String coordsLine = "X: " + fmt(m.x) + "  Y: " + fmt(m.y) + "  Z: " + fmt(m.z);

        int nameW = tr.getWidth(Text.literal(name));
        int distW = tr.getWidth(Text.literal(distLine));
        int coordW = tr.getWidth(Text.literal(coordsLine));
        float textW = Math.max(nameW, Math.max(distW, coordW));

        float w = textW + PAD_X * 2;
        float h = LINE_HEIGHT * 3 + PAD_Y * 2;
        float left = -w / 2f;
        float top = -h / 2f;

        int a = (int) (alpha * 255);

        // Внешнее свечение рамки (аддитив, поверх фона по краю).
        drawRoundedBorder(entry, consumers.getBuffer(GLOW), left, top, w, h,
                CORNER_R + 1.5f, 3.0f, ir(color), ig(color), ib(color), (int) (a * 0.45f));
        // Тёмный полупрозрачный фон.
        drawRoundedFill(entry, consumers.getBuffer(SEE_THROUGH), left, top, w, h,
                CORNER_R, 12, 13, 20, (int) (a * 0.90f));
        // Чёткая светящаяся рамка цвета метки.
        drawRoundedBorder(entry, consumers.getBuffer(SEE_THROUGH), left, top, w, h,
                CORNER_R, 1.3f, ir(color), ig(color), ib(color), (int) (a * 0.95f));

        // Текст (3 строки по центру).
        Matrix4f pos = entry.getPositionMatrix();
        float y = top + PAD_Y;

        int nameCol = (a << 24) | 0xFFFFFF;
        float nx = -nameW / 2f;
        tr.draw(Text.literal(name), nx, y, nameCol, true, pos, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

        y += LINE_HEIGHT;
        int distCol = (a << 24) | (color & 0xFFFFFF);
        float dx = -distW / 2f;
        tr.draw(Text.literal(distLine), dx, y, distCol, true, pos, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

        y += LINE_HEIGHT;
        int coordCol = (a << 24) | 0xBFD9FF;
        float cx = -coordW / 2f;
        tr.draw(Text.literal(coordsLine), cx, y, coordCol, true, pos, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private static String fmt(double v) {
        long l = Math.round(v);
        if (Math.abs(v - l) < 1e-6) return Long.toString(l);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ shapes

    private static void drawRing(MatrixStack.Entry entry, VertexConsumerProvider consumers,
                                 int r, int g, int b, float alpha, float pulse) {
        VertexConsumer buf = consumers.getBuffer(SEE_THROUGH);
        float inner = 0.42f + 0.05f * pulse;
        float outer = 0.62f + 0.08f * pulse;
        int a = (int) (alpha * 255 * 0.75f);
        float y = 0.02f;
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = (2 * Math.PI * i) / SEGMENTS;
            double a2 = (2 * Math.PI * (i + 1)) / SEGMENTS;
            float x1o = (float) (Math.cos(a1) * outer), z1o = (float) (Math.sin(a1) * outer);
            float x2o = (float) (Math.cos(a2) * outer), z2o = (float) (Math.sin(a2) * outer);
            float x1i = (float) (Math.cos(a1) * inner), z1i = (float) (Math.sin(a1) * inner);
            float x2i = (float) (Math.cos(a2) * inner), z2i = (float) (Math.sin(a2) * inner);
            vert(entry, buf, x1o, y, z1o, r, g, b, a);
            vert(entry, buf, x1i, y, z1i, r, g, b, a);
            vert(entry, buf, x2o, y, z2o, r, g, b, a);

            vert(entry, buf, x2o, y, z2o, r, g, b, a);
            vert(entry, buf, x1i, y, z1i, r, g, b, a);
            vert(entry, buf, x2i, y, z2i, r, g, b, a);
        }
    }

    private static void drawBeam(MatrixStack.Entry entry, VertexConsumerProvider consumers,
                                 int r, int g, int b, float alpha, float topY) {
        VertexConsumer buf = consumers.getBuffer(SEE_THROUGH);
        float w = 0.10f;
        int bottom = (int) (alpha * 255 * 0.80f);
        int top = (int) (alpha * 255 * 0.08f);
        quad(entry, buf, -w, 0, 0, w, 0, 0, -w, topY, 0, w, topY, 0, r, g, b, bottom, top);
        quad(entry, buf, 0, 0, -w, 0, 0, w, 0, topY, -w, 0, topY, w, r, g, b, bottom, top);
    }

    // ------------------------------------------------------------------ rounded rect

    /** Точки периметра скруглённого прямоугольника (по часовой/против — не важно, cull выкл). */
    private static List<float[]> perimeter(float x, float y, float w, float h, float rad) {
        float r = Math.max(0.0f, Math.min(rad, Math.min(w, h) / 2f));
        List<float[]> pts = new ArrayList<>();
        // центры углов и дуги: TL, TR, BR, BL
        addArc(pts, x + r, y + r, r, 180, 270);
        addArc(pts, x + w - r, y + r, r, 270, 360);
        addArc(pts, x + w - r, y + h - r, r, 0, 90);
        addArc(pts, x + r, y + h - r, r, 90, 180);
        return pts;
    }

    private static void addArc(List<float[]> pts, float cx, float cy, float r, float degFrom, float degTo) {
        for (int i = 0; i <= CORNER_SEG; i++) {
            double t = Math.toRadians(degFrom + (degTo - degFrom) * (i / (double) CORNER_SEG));
            pts.add(new float[]{cx + (float) (Math.cos(t) * r), cy + (float) (Math.sin(t) * r)});
        }
    }

    /** Заливка скруглённого прямоугольника (веер из центра). */
    private static void drawRoundedFill(MatrixStack.Entry entry, VertexConsumer buf,
                                        float x, float y, float w, float h, float rad,
                                        int r, int g, int b, int a) {
        List<float[]> p = perimeter(x, y, w, h, rad);
        float cx = x + w / 2f, cy = y + h / 2f;
        for (int i = 0; i < p.size(); i++) {
            float[] cur = p.get(i);
            float[] nxt = p.get((i + 1) % p.size());
            vert2(entry, buf, cx, cy, r, g, b, a);
            vert2(entry, buf, cur[0], cur[1], r, g, b, a);
            vert2(entry, buf, nxt[0], nxt[1], r, g, b, a);
        }
    }

    /** Рамка скруглённого прямоугольника (полоса между внешним и внутренним периметром). */
    private static void drawRoundedBorder(MatrixStack.Entry entry, VertexConsumer buf,
                                          float x, float y, float w, float h, float rad, float thick,
                                          int r, int g, int b, int a) {
        List<float[]> outer = perimeter(x, y, w, h, rad);
        List<float[]> inner = perimeter(x + thick, y + thick, w - thick * 2, h - thick * 2,
                Math.max(0.0f, rad - thick));
        int n = Math.min(outer.size(), inner.size());
        for (int i = 0; i < n; i++) {
            float[] o1 = outer.get(i);
            float[] o2 = outer.get((i + 1) % n);
            float[] i1 = inner.get(i);
            float[] i2 = inner.get((i + 1) % n);
            vert2(entry, buf, o1[0], o1[1], r, g, b, a);
            vert2(entry, buf, o2[0], o2[1], r, g, b, a);
            vert2(entry, buf, i2[0], i2[1], r, g, b, a);

            vert2(entry, buf, o1[0], o1[1], r, g, b, a);
            vert2(entry, buf, i2[0], i2[1], r, g, b, a);
            vert2(entry, buf, i1[0], i1[1], r, g, b, a);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Прямоугольник из двух треугольников с вертикальным градиентом альфы (bottom→top). */
    private static void quad(MatrixStack.Entry entry, VertexConsumer buf,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b, int bottom, int top) {
        vert(entry, buf, x1, y1, z1, r, g, b, bottom);
        vert(entry, buf, x2, y2, z2, r, g, b, bottom);
        vert(entry, buf, x3, y3, z3, r, g, b, top);

        vert(entry, buf, x3, y3, z3, r, g, b, top);
        vert(entry, buf, x2, y2, z2, r, g, b, bottom);
        vert(entry, buf, x4, y4, z4, r, g, b, top);
    }

    private static void vert(MatrixStack.Entry entry, VertexConsumer buf,
                             float x, float y, float z, int r, int g, int b, int a) {
        buf.vertex(entry, x, y, z).color(r, g, b, a);
    }

    /** Вершина в локальной 2D-плоскости панели (z=0). */
    private static void vert2(MatrixStack.Entry entry, VertexConsumer buf,
                              float x, float y, int r, int g, int b, int a) {
        buf.vertex(entry, x, y, 0f).color(r, g, b, a);
    }

    private static int ir(int c) { return (c >> 16) & 0xFF; }
    private static int ig(int c) { return (c >> 8) & 0xFF; }
    private static int ib(int c) { return c & 0xFF; }
}
