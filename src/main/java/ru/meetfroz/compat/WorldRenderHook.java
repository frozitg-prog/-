package ru.meetfroz.compat;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Версия-независимая точка входа отрисовки в мире.
 *
 * Общий код (GPSRenderer) не должен ссылаться на Fabric-контекст отрисовки,
 * потому что его API меняется между версиями (1.21.4–1.21.8 — старый
 * {@code v1.WorldRenderEvents}, 1.21.10/11 — новый {@code v1.world.*},
 * 1.21.9 — событий нет вовсе). Вместо этого каждая версия сама добывает
 * эти три устойчивых между версиями объекта (MatrixStack / потребитель
 * вершин / камера) и передаёт их сюда.
 *
 * Все три типа стабильны в 1.21.4–1.21.11, поэтому интерфейс живёт в общем
 * коде и одинаков для всех версий.
 */
@FunctionalInterface
public interface WorldRenderHook {

    /**
     * Вызывается один раз за кадр во время прохода отрисовки мира.
     *
     * @param matrices   матрица мира (обычно в системе координат относительно
     *                   камеры: нулевая точка — позиция камеры);
     * @param consumers  потребитель вершин, в который нужно писать геометрию;
     * @param camera     камера (для yaw/pitch биллборда и позиции).
     */
    void onWorldRender(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera);
}
