package ru.meetfroz.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа клиентского мода MeetFroz (только клиент, сервер мода не требует).
 *
 * Компоненты:
 *  - GPSManager            — хранение/список/персистентность меток;
 *  - GPSCommand            — клиентские команды .gps ...;
 *  - ChatCoordinateHandler — создание метки кликом по координатам в чате
 *                            (триггерит версия-специфичный миксин ChatScreenMixin);
 *  - GPSRenderer           — анимации и вызовы отрисовки;
 *  - RenderCompat          — версия-специфичный рендер меток (сквозь стены).
 */
public final class MeetFroz implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("MeetFroz");
    public static GPSManager manager;
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        manager = new GPSManager();
        manager.load();
        MeetFrozConfig.load();
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.meetfroz.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "category.meetfroz"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new MeetFrozScreen());
            }
        });

        GPSCommand.register();
        FriendsManager.register();
        GPSRenderer.register();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (manager != null) manager.save();
            MeetFrozConfig.get().save();
        });

        LOGGER.info("MeetFroz инициализирован (клиент, GPS-метки активны)");
    }

    /** Системное сообщение игроку в чат (не зависит от сервера). */
    public static void notifyPlayer(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§b[MeetFroz] §r" + message), false);
        } else {
            LOGGER.info("[notify] {}", message);
        }
    }
}
