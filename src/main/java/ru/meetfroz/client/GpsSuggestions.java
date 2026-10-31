package ru.meetfroz.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Автодополнение локальной команды .qgps в чате: список, подсказки аргументов и TAB. */
public final class GpsSuggestions {

    public record Row(String text, String desc, int color, boolean completable) {
    }

    private static final String PREFIX = ".qgps";
    private static final String ADD_FORMAT = ".qgps add <name> <x> <y> <z>";

    private static final String[][] SUBS = {
            {"add", "создать новую GPS-метку"},
            {"remove", "удалить метку"},
            {"clear", "удалить все метки"},
            {"off", "отключить отображение"},
            {"on", "включить отображение"},
            {"list", "показать список меток"},
            {"help", "справка по командам"}
    };

    private static String baseText;
    private static int index = -1;
    private static String applied;

    private GpsSuggestions() {
    }

    public static void reset() {
        baseText = null;
        applied = null;
        index = -1;
    }

    private static boolean isCommand(String text) {
        return text != null && (text.equals(PREFIX) || text.startsWith(PREFIX + " "));
    }

    public static List<Row> rows(String text) {
        List<Row> out = new ArrayList<>();
        if (!isCommand(text)) return out;

        String[] parts = text.split(" ", -1);
        if (parts.length <= 2) {
            String partial = parts.length == 2 ? parts[1] : "";
            for (String[] sub : SUBS) {
                if (sub[0].startsWith(partial.toLowerCase(Locale.ROOT))) {
                    out.add(new Row(sub[0], sub[1], 0xFFFFFFFF, true));
                }
            }
        } else if (parts[1].equalsIgnoreCase("remove")) {
            if (parts.length == 3) {
                String partial = parts[2].toLowerCase(Locale.ROOT);
                if (MeetFroz.manager != null) {
                    for (GPSMarker marker : MeetFroz.manager.snapshot()) {
                        if (marker.name != null
                                && marker.name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            out.add(new Row(marker.name, marker.coordsString(), 0xFFFFD266, true));
                        }
                    }
                }
            }
        } else if (parts[1].equalsIgnoreCase("add")) {
            out.add(new Row(ADD_FORMAT, null, 0xFFFFFF66, false));
        }
        return out;
    }

    public static boolean onTab(String text, Consumer<String> setText) {
        if (!isCommand(text)) return false;

        if (!text.equals(applied)) {
            baseText = text;
            applied = null;
            index = -1;
        }

        List<Row> options = new ArrayList<>();
        for (Row row : rows(baseText)) {
            if (row.completable) options.add(row);
        }
        if (options.isEmpty()) {
            reset();
            return false;
        }

        index = (index + 1) % options.size();
        String result = apply(baseText, options.get(index));
        applied = result;
        setText.accept(result);
        return true;
    }

    private static String apply(String base, Row row) {
        int space = base.lastIndexOf(' ');
        String prefix = space < 0 ? base + " " : base.substring(0, space + 1);
        return prefix + row.text;
    }

    public static void render(DrawContext context, String text) {
        List<Row> rows = rows(text);
        if (rows.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer renderer = mc.textRenderer;
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int lineHeight = 10;
        int maxWidth = 0;
        for (Row row : rows) {
            int width = renderer.getWidth(row.text);
            if (row.desc != null) width += 8 + renderer.getWidth(row.desc);
            maxWidth = Math.max(maxWidth, width);
        }
        maxWidth = Math.min(maxWidth, screenWidth - 12);

        int popupHeight = rows.size() * lineHeight + 6;
        int x = 4;
        int yTop = screenHeight - 14 - 8 - popupHeight;

        context.fill(x - 3, yTop - 3, x + maxWidth + 6, yTop + popupHeight + 3, 0xB008080F);
        context.fill(x - 3, yTop - 3, x + maxWidth + 6, yTop - 2, 0xFF5A7BFF);

        int selected = (baseText != null && baseText.equals(text)) ? index : -1;
        int y = yTop;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (i == selected) {
                context.fill(x - 2, y - 1, x + maxWidth + 4, y + lineHeight - 2, 0x405A7BFF);
            }
            context.drawTextWithShadow(renderer, row.text, x, y, row.color);
            if (row.desc != null) {
                int offset = x + renderer.getWidth(row.text) + 8;
                if (offset + renderer.getWidth(row.desc) < screenWidth - 6) {
                    context.drawTextWithShadow(renderer, row.desc, offset, y, 0xFF9A9AA8);
                }
            }
            y += lineHeight;
        }
    }
}
