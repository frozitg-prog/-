package ru.meetfroz.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import java.util.List;

public final class MeetFrozScreen extends Screen {
    private enum Tab { MARKERS, ARROW, COSMETICS }
    private Tab tab = Tab.MARKERS;
    private TextFieldWidget markerName, markerX, markerY, markerZ;
    private int scroll;

    public MeetFrozScreen() { super(Text.literal("MeetFroz")); }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int top = this.height / 2 - 105;
        addDrawableChild(ButtonWidget.builder(Text.literal("Метки"), b -> { tab = Tab.MARKERS; clearAndInit(); }).dimensions(left, top, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Стрелка"), b -> { tab = Tab.ARROW; clearAndInit(); }).dimensions(left + 102, top, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Косметика"), b -> { tab = Tab.COSMETICS; clearAndInit(); }).dimensions(left + 204, top, 96, 20).build());

        if (tab == Tab.MARKERS) initMarkers(left, top);
        else if (tab == Tab.ARROW) initArrow(left, top);
        else initCosmetics(left, top);
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close()).dimensions(left + 204, top + 184, 96, 20).build());
    }

    private void initMarkers(int left, int top) {
        MeetFrozConfig c = MeetFrozConfig.get();
        addDrawableChild(ButtonWidget.builder(Text.literal("Метки: " + (c.labelsEnabled ? "ВКЛ" : "ВЫКЛ")), b -> {
            c.labelsEnabled = !c.labelsEnabled; c.save(); clearAndInit();
        }).dimensions(left, top + 28, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Размер текста: " + c.labelScale + "%"), b -> {
            c.labelScale = c.labelScale >= 200 ? 50 : c.labelScale + 25; c.save(); clearAndInit();
        }).dimensions(left + 155, top + 28, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Цвет меток: " + colorName(c.labelColor)), b -> {
            c.labelColor = nextColor(c.labelColor); c.save(); clearAndInit();
        }).dimensions(left, top + 52, 145, 20).build());

        markerName = new TextFieldWidget(textRenderer, left, top + 82, 145, 20, Text.literal("Название"));
        markerName.setPlaceholder(Text.literal("Название метки"));
        markerX = new TextFieldWidget(textRenderer, left + 155, top + 82, 45, 20, Text.literal("X"));
        markerX.setPlaceholder(Text.literal("X"));
        markerY = new TextFieldWidget(textRenderer, left + 205, top + 82, 45, 20, Text.literal("Y"));
        markerY.setPlaceholder(Text.literal("Y"));
        markerZ = new TextFieldWidget(textRenderer, left + 255, top + 82, 45, 20, Text.literal("Z"));
        markerZ.setPlaceholder(Text.literal("Z"));
        addDrawableChild(markerName); addDrawableChild(markerX); addDrawableChild(markerY); addDrawableChild(markerZ);
        addDrawableChild(ButtonWidget.builder(Text.literal("Добавить метку"), b -> addMarker()).dimensions(left, top + 106, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Добавить мою позицию"), b -> {
            if (client != null && client.player != null && client.world != null) {
                var p = client.player.getPos();
                MeetFroz.manager.add("Метка " + (MeetFroz.manager.count() + 1), p.x, p.y, p.z,
                    client.world.getRegistryKey().getValue().toString());
                MeetFroz.manager.save();
            }
        }).dimensions(left + 155, top + 106, 145, 20).build());

        List<GPSMarker> markers = MeetFroz.manager.snapshot();
        int start = Math.min(scroll, Math.max(0, markers.size() - 4));
        for (int i = start; i < Math.min(markers.size(), start + 4); i++) {
            GPSMarker m = markers.get(i);
            int row = i - start;
            addDrawableChild(ButtonWidget.builder(Text.literal("Удалить: " + m.name), b -> {
                MeetFroz.manager.remove(m.name); MeetFroz.manager.save(); clearAndInit();
            }).dimensions(left, top + 132 + row * 19, 145, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(m.name + "  " + m.coordsString()), b -> {
                markerName.setText(m.name); markerX.setText(Integer.toString((int)m.x));
                markerY.setText(Integer.toString((int)m.y)); markerZ.setText(Integer.toString((int)m.z));
            }).dimensions(left + 155, top + 132 + row * 19, 145, 18).build());
        }
        if (markers.size() > 4) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Ещё ↓"), b -> { scroll++; clearAndInit(); })
                .dimensions(left + 155, top + 132 + 4 * 19, 145, 18).build());
        }
    }

    private void addMarker() {
        try {
            String name = markerName.getText().isBlank() ? "Метка" : markerName.getText();
            double x = Double.parseDouble(markerX.getText());
            double y = Double.parseDouble(markerY.getText());
            double z = Double.parseDouble(markerZ.getText());
            if (client != null && client.world != null) {
                MeetFroz.manager.add(name, x, y, z, client.world.getRegistryKey().getValue().toString());
                MeetFroz.manager.save(); clearAndInit();
            }
        } catch (NumberFormatException ignored) {
            markerName.setPlaceholder(Text.literal("Проверь координаты"));
        }
    }

    private void initArrow(int left, int top) {
        MeetFrozConfig c = MeetFrozConfig.get();
        addDrawableChild(ButtonWidget.builder(Text.literal("Стрелка: " + (c.arrowEnabled ? "ВКЛ" : "ВЫКЛ")), b -> {
            c.arrowEnabled = !c.arrowEnabled; c.save(); clearAndInit();
        }).dimensions(left, top + 32, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Расстояние: " + (c.arrowShowDistance ? "ВКЛ" : "ВЫКЛ")), b -> {
            c.arrowShowDistance = !c.arrowShowDistance; c.save(); clearAndInit();
        }).dimensions(left + 155, top + 32, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Размер: " + c.arrowScale + "%"), b -> {
            c.arrowScale = c.arrowScale >= 200 ? 50 : c.arrowScale + 25; c.save(); clearAndInit();
        }).dimensions(left, top + 58, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Цвет: " + colorName(c.arrowColor)), b -> {
            c.arrowColor = nextColor(c.arrowColor); c.save(); clearAndInit();
        }).dimensions(left + 155, top + 58, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить выбор метки"), b -> {
            ArrowTarget.clear();
        }).dimensions(left, top + 88, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Выбрать следующую метку"), b -> {
            ArrowTarget.selectNext();
        }).dimensions(left, top + 114, 300, 20).build());
    }

    private void initCosmetics(int left, int top) {
        String[] options = {"none", "cape_star", "cape_night", "wings_white", "wings_gold"};
        String[] names = {"Нет", "Плащ: звёзды", "Плащ: ночь", "Крылья: белые", "Крылья: золотые"};
        for (int i = 0; i < options.length; i++) {
            final String value = options[i];
            addDrawableChild(ButtonWidget.builder(Text.literal((MeetFrozConfig.get().cosmetic.equals(value) ? "✓ " : "") + names[i]), b -> {
                MeetFrozConfig.get().cosmetic = value; MeetFrozConfig.get().save(); clearAndInit();
            }).dimensions(left, top + 34 + i * 25, 300, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Косметика отображается только у тебя"), b -> {}).dimensions(left, top + 164, 300, 20).build());
    }

    private static int nextColor(int current) {
        int[] colors = {0x55FFFF, 0x55FF55, 0xFF55FF, 0xFFFF55, 0xFF5555, 0xFFFFFF};
        for (int i = 0; i < colors.length; i++) if (colors[i] == current) return colors[(i + 1) % colors.length];
        return colors[0];
    }
    private static String colorName(int color) { return String.format("#%06X", color & 0xFFFFFF); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, "MeetFroz", width / 2, height / 2 - 122, 0xFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }
}
