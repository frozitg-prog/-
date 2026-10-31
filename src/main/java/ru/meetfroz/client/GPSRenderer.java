package ru.meetfroz.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import ru.meetfroz.compat.RenderCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Оркестрация отрисовки GPS-меток в мире.
 *
 * Здесь общая логика: выбор меток текущего измерения, сортировка по дальности,
 * расчёт анимаций (появление/погасание, пульс, вращение) и цвета. Сам рендер
 * геометрии (столб, кольцо) и подписей выполняется версия-специфичным
 * {@link RenderCompat}, чтобы не дублировать меняющийся между версиями API.
 */
public final class GPSRenderer {

    /**
     * Дальность отрисовки. Метка и информационная панель рисуются одним вызовом,
     * поэтому текст виден ровно так же далеко, как и сам маркер (2048 блоков —
     * увеличено с 1024). Панель дополнительно масштабируется по расстоянию
     * в RenderCompat, чтобы текст оставался читаемым.
     */
    private static final double MAX_RENDER_DIST = 2048.0;

    private GPSRenderer() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(GPSRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!MeetFroz.manager.isEnabled()) return;

        var matrices = context.matrixStack();
        var consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        String dim = mc.world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();

        List<GPSMarker> list = new ArrayList<>();
        for (GPSMarker m : MeetFroz.manager.snapshot()) {
            if (!m.dimension.equals(dim)) continue;
            double d = m.squaredDistanceTo(cam.x, cam.y, cam.z);
            if (d > MAX_RENDER_DIST * MAX_RENDER_DIST) continue;
            list.add(m);
        }
        if (list.isEmpty()) return;

        // Ближние метки рисуем первыми — подписи корректно перекрываются.
        list.sort(Comparator.comparingDouble(m -> m.squaredDistanceTo(cam.x, cam.y, cam.z)));

        for (GPSMarker m : list) {
            float fade = fadeInOut(m.createdAt, now);
            float anim = (float) ((now % 4000L) / 4000.0); // фаза вращения 0..1
            int color = colorFor(m.name);
            RenderCompat.drawMarker(context, m, cam.x, cam.y, cam.z, fade, anim, color);
        }
    }

    /** Плавное появление метки (0..1) на основе времени создания. */
    private static float fadeInOut(long createdAt, long now) {
        long age = now - createdAt;
        long dur = 700L;
        if (age >= dur) return 1.0f;
        if (age <= 0) return 0.0f;
        float t = age / (float) dur;
        return t * t * (3.0f - 2.0f * t); // smoothstep
    }

    /** Стабильный приятный цвет из названия метки. */
    private static int colorFor(String name) {
        int h = name.hashCode();
        // избегаем слишком тёмных/красных оттенков — берём сочную палитру
        float hue = Math.floorMod(h, 360) / 360.0f;
        return hsvToRgb(hue, 0.55f, 1.0f);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
