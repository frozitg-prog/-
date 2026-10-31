package ru.meetfroz.client;

/**
 * Модель GPS-метки: название, координаты, измерение и время создания.
 * Поля публичные — мод сериализует список меток в JSON (Gson).
 */
public class GPSMarker {

    public String name;
    public double x;
    public double y;
    public double z;
    /** Идентификатор измерения, например minecraft:overworld. */
    public String dimension;
    /** Время создания (epoch millis) — для анимации появления. */
    public long createdAt;

    public GPSMarker() {
    }

    public GPSMarker(String name, double x, double y, double z, String dimension) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.createdAt = System.currentTimeMillis();
    }

    /** Квадрат расстояния до точки (для сортировки). */
    public double squaredDistanceTo(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Расстояние в блоках (метрах) до точки. */
    public double distanceTo(double px, double py, double pz) {
        return Math.sqrt(squaredDistanceTo(px, py, pz));
    }

    /** Строка координат вида «125 75 -340» (целые, если значение целое). */
    public String coordsString() {
        return fmt(x) + " " + fmt(y) + " " + fmt(z);
    }

    private static String fmt(double v) {
        long l = Math.round(v);
        if (Math.abs(v - l) < 1e-6) return Long.toString(l);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Точные координаты совпадают (с допуском) с другой меткой? */
    public boolean sameCoords(GPSMarker other) {
        return sameCoordsAt(other.x, other.y, other.z);
    }

    /** Точные координаты совпадают (с допуском) с точкой? */
    public boolean sameCoordsAt(double px, double py, double pz) {
        return Math.abs(x - px) < 1e-3
                && Math.abs(y - py) < 1e-3
                && Math.abs(z - pz) < 1e-3;
    }
}
