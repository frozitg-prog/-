package ru.meetfroz.client;

import net.minecraft.client.MinecraftClient;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общая (не зависящая от версии) логика клика по координатам в чате.
 *
 * Сюда приходит уже найденная строка чата и индекс символа, по которому кликнул
 * игрок (их вычисляет версия-специфичный миксин ChatHudMixin). Здесь распознаются
 * тройки координат «x y z» и двойки «x z», и создаётся GPS-метка со сдвигом Y+5.
 */
public final class ChatCoordinateHandler {

    // Тройка целых: x y z (разделители — пробел/запятая/точка с запятой).
    // Не захватывает часть более длинного числа (\b-защита через lookaround).
    private static final Pattern TRIPLE = Pattern.compile(
            "(?<![\\w.+-])([+-]?\\d{1,7})\\s*[,; ]\\s*([+-]?\\d{1,7})\\s*[,; ]\\s*([+-]?\\d{1,7})(?![\\w.+-])");

    // Двойка целых: x z (без Y).
    private static final Pattern DOUBLE = Pattern.compile(
            "(?<![\\w.+-])([+-]?\\d{1,7})\\s*[,; ]\\s*([+-]?\\d{1,7})(?![\\w.+-])");

    private ChatCoordinateHandler() {
    }

    /**
     * Пытается создать метку по клику игрока.
     *
     * @param lineText  полный текст строки чата, по которой кликнули (null — мимо)
     * @param charIndex индекс символа в строке, куда указывает курсор
     * @return true, если метка создана и клик нужно погасить
     */
    public static boolean handleClick(String lineText, int charIndex) {
        if (lineText == null || lineText.isEmpty()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        // Сначала ищем тройку (x y z), затем двойку (x z).
        Optional<double[]> triple = matchAt(TRIPLE, lineText, charIndex, 3);
        double x, y, z;
        if (triple.isPresent()) {
            double[] v = triple.get();
            x = v[0];
            y = v[1] + 5.0; // по требованию — прибавляем 5 к высоте
            z = v[2];
        } else {
            Optional<double[]> dbl = matchAt(DOUBLE, lineText, charIndex, 2);
            if (dbl.isEmpty()) return false;
            double[] v = dbl.get();
            x = v[0];
            y = Math.floor(mc.player.getY()) + 5.0; // Y берём у игрока + 5
            z = v[1];
        }

        String dim = mc.world.getRegistryKey().getValue().toString();
        GPSManager mgr = MeetFroz.manager;
        Optional<GPSMarker> created = mgr.addFromChat(x, y, z, dim);
        if (created.isEmpty()) {
            MeetFroz.notifyPlayer("§eКоординаты " + (int) x + " " + (int) y + " " + (int) z
                    + " уже помечены — дубликат не создан.");
            return true;
        }
        GPSMarker m = created.get();
        double dist = m.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        MeetFroz.notifyPlayer("§aGPS-метка создана: §f" + m.name + " §7(" + m.coordsString()
                + "§7, " + (int) Math.round(dist) + " м)");
        return true;
    }

    /**
     * Ищет совпадение паттерна так, чтобы кликнутый индекс попадал внутрь матча.
     * Возвращает координаты чисел либо пусто.
     */
    private static Optional<double[]> matchAt(Pattern p, String text, int charIndex, int groups) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (charIndex >= start && charIndex <= end) {
                double[] out = new double[groups];
                for (int g = 0; g < groups; g++) {
                    try {
                        out[g] = Double.parseDouble(m.group(g + 1));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                }
                return Optional.of(out);
            }
        }
        return Optional.empty();
    }
}
