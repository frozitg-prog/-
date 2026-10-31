package ru.meetfroz.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Настоящее автодополнение клиентской команды {@code .gps}:
 *  - всплывающие подсказки прямо при вводе в чате (список команд с описанием,
 *    формат {@code .gps add <name> <x> <y> <z>}, названия существующих меток);
 *  - циклический выбор по TAB.
 *
 * Совместимо со способом обработки команд (.gps перехватывается локально через
 * ClientSendMessageEvents.ALLOW_CHAT), существующая обработка не меняется.
 *
 * Вызывается версия-специфичным миксином ChatScreenMixin (TAB + отрисовка попапа).
 */
public final class GpsSuggestions {

    /** Строка подсказки. {@code completable=false} — статическая подсказка (формат), TAB её не вставляет. */
    public record Row(String text, String desc, int color, boolean completable) {
    }

    private static final String PREFIX = ".gps";
    private static final String ADD_FORMAT = ".gps add <name> <x> <y> <z>";

    private static final String[][] SUBS = {
            {"add", "создать новую GPS-метку"},
            {"remove", "удалить метку"},
            {"clear", "удалить все метки"},
            {"off", "отключить отображение"},
            {"on", "включить отображение"},
            {"list", "показать список меток"},
            {"help", "справка по командам"},
    };

    // Состояние циклического TAB.
    private static String baseText;
    private static int index = -1;
    private static String applied;

    private GpsSuggestions() {
    }

    /** Сброс цикла (вызывается при закрытии чата). */
    public static void reset() {
        baseText = null;
        applied = null;
        index = -1;
    }

    /** Относится ли текст к команде .gps (ровно ".gps" или начинается с ".gps "). */
    private static boolean isGps(String text) {
        return text != null && (text.equals(PREFIX) || text.startsWith(PREFIX + " "));
    }

    // ------------------------------------------------------------------ подсказки

    /** Все подсказки для текущего текста (TAB-варианты + статические форматы). */
    public static List<Row> rows(String text) {
        List<Row> out = new ArrayList<>();
        if (!isGps(text)) return out;
        String[] p = text.split(" ", -1);

        if (p.length <= 2) {
            // .gps | .gps <часть> | .gps <часть><пробел>
            String partial = p.length == 2 ? p[1] : "";
            for (String[] s : SUBS) {
                if (s[0].startsWith(partial)) {
                    out.add(new Row(s[0], s[1], 0xFFFFFFFF, true));
                }
            }
        } else if (p[1].equalsIgnoreCase("remove")) {
            if (p.length == 3) {
                String partial = p[2].toLowerCase(Locale.ROOT);
                for (GPSMarker m : MeetFroz.manager.snapshot()) {
                    if (m.name != null && m.name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                        out.add(new Row(m.name, m.coordsString(), 0xFFFFD266, true));
                    }
                }
            }
        } else if (p[1].equalsIgnoreCase("add")) {
            // формат команды — статическая подсказка
            out.add(new Row(ADD_FORMAT, null, 0xFFFFFF66, false));
        }
        return out;
    }

    // ------------------------------------------------------------------ TAB

    /**
     * Обработка TAB. Возвращает true, если подсказка применена (текст установлен
     * через {@code setText}), и клавиша должна быть погашена.
     */
    public static boolean onTab(String text, Consumer<String> setText) {
        if (!isGps(text)) return false;

        // Текст изменили не мы (первый TAB или игрок продолжил печатать) — новый цикл.
        if (!text.equals(applied)) {
            baseText = text;
            applied = null;
            index = -1;
        }

        List<Row> opts = new ArrayList<>();
        for (Row r : rows(baseText)) {
            if (r.completable) opts.add(r);
        }
        if (opts.isEmpty()) {
            reset();
            return false;
        }

        index = (index + 1) % opts.size();
        String out = apply(baseText, opts.get(index));
        applied = out;
        setText.accept(out);
        return true;
    }

    /** Подставляет вариант в базовый текст (замена текущего токена). */
    private static String apply(String base, Row r) {
        int sp = base.lastIndexOf(' ');
        String prefix = sp < 0 ? base + " " : base.substring(0, sp + 1);
        return prefix + r.text;
    }

    // ------------------------------------------------------------------ отрисовка попапа

    /** Рисует всплывающий список подсказок над полем ввода чата. */
    public static void render(DrawContext ctx, String text) {
        List<Row> rows = rows(text);
        if (rows.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        int lineH = 10;
        int maxW = 0;
        for (Row r : rows) {
            int w = tr.getWidth(r.text);
            if (r.desc != null) w += 8 + tr.getWidth(r.desc);
            if (w > maxW) maxW = w;
        }
        maxW = Math.min(maxW, screenW - 12);

        int popupH = rows.size() * lineH + 6;
        int x = 4;
        int yTop = screenH - 14 - 8 - popupH; // над полем ввода

        // полупрозрачный тёмный фон + тонкая рамка-акцент
        ctx.fill(x - 3, yTop - 3, x + maxW + 6, yTop + popupH + 3, 0xB008080F);
        ctx.fill(x - 3, yTop - 3, x + maxW + 6, yTop - 2, 0xFF5A7BFF);

        // подсветка выбранного TAB-варианта
        int selected = (baseText != null && baseText.equals(text)) ? index : -1;

        int y = yTop;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i == selected) {
                ctx.fill(x - 2, y - 1, x + maxW + 4, y + lineH - 2, 0x405A7BFF);
            }
            ctx.drawTextWithShadow(tr, r.text, x, y, r.color);
            if (r.desc != null) {
                int off = x + tr.getWidth(r.text) + 8;
                if (off + tr.getWidth(r.desc) < screenW - 6) {
                    ctx.drawTextWithShadow(tr, r.desc, off, y, 0xFF9A9AA8);
                }
            }
            y += lineH;
        }
    }
}
