package ru.meetfroz.client;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Клиентские команды «.gps ...». Перехватываются локально и никогда не уходят
 * на сервер Minecraft (работают и в одиночке, и в мультиплеере без мода на сервере).
 *
 * .gps <name> <x> <y> <z>  — создать метку (Y можно опустить)
 * .gps <name> <x> <z>      — создать метку, Y берётся у игрока
 * .gps list                — список меток
 * .gps remove <name>       — удалить метку
 * .gps clear               — удалить все метки
 * .gps off / .gps on       — выключить/включить отображение
 * .gps help                — справка
 */
public final class GPSCommand {

    private static final String PREFIX = ".gps";

    private GPSCommand() {
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String trimmed = message.trim();
            if (!trimmed.startsWith(".")) return true;
            String[] parts = trimmed.split("\\s+");
            if (!parts[0].toLowerCase(Locale.ROOT).equals(PREFIX)) return true;
            handle(parts);
            return false; // гасим отправку в чат сервера
        });
    }

    private static void handle(String[] parts) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (parts.length == 1) {
            usage(null);
            return;
        }
        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> usage(null);
            case "list" -> list();
            case "clear" -> {
                MeetFroz.manager.clear();
                MeetFroz.manager.save();
                notifyNow("§aВсе GPS-метки удалены.");
            }
            case "off" -> {
                MeetFroz.manager.setEnabled(false);
                notifyNow("§cОтображение GPS-меток выключено.");
            }
            case "on" -> {
                MeetFroz.manager.setEnabled(true);
                notifyNow("§aОтображение GPS-меток включено.");
            }
            case "remove" -> {
                if (parts.length < 3) {
                    usage("remove <name>");
                    return;
                }
                String name = parts[2];
                boolean ok = MeetFroz.manager.remove(name);
                MeetFroz.manager.save();
                notifyNow(ok ? "§aМетка §f" + name + " §aудалена." : "§cМетка §f" + name + " §cне найдена.");
            }
            default -> createMarker(parts, mc);
        }
    }

    private static void createMarker(String[] parts, MinecraftClient mc) {
        // .gps <name> <x> <y> <z>   (parts[0]=.gps, [1]=name)
        // .gps <name> <x> <z>
        if (parts.length != 4 && parts.length != 5) {
            usage("<name> <x> <y> <z>");
            return;
        }
        String name = parts[1];
        double x, y, z;
        try {
            x = Double.parseDouble(parts[2]);
            if (parts.length == 5) {
                y = Double.parseDouble(parts[3]);
                z = Double.parseDouble(parts[4]);
            } else {
                z = Double.parseDouble(parts[3]);
                if (mc.player == null) {
                    notifyNow("§cИгрок недоступен — нельзя определить Y.");
                    return;
                }
                y = Math.floor(mc.player.getY());
            }
        } catch (NumberFormatException e) {
            notifyNow("§cКоординаты должны быть числами. Пример: §f.gps Spawn 100 64 -250");
            return;
        }

        String dim = mc.world != null
                ? mc.world.getRegistryKey().getValue().toString()
                : "minecraft:overworld";

        Optional<GPSMarker> created = MeetFroz.manager.add(name, x, y, z, dim);
        MeetFroz.manager.save();
        if (created.isEmpty()) {
            notifyNow("§eМетка с координатами §f" + (int) x + " " + (int) y + " " + (int) z
                    + " §eуже существует (дубликат).");
            return;
        }
        GPSMarker m = created.get();
        double dist = mc.player != null
                ? m.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                : 0;
        notifyNow("§aGPS-метка §f" + m.name + " §asоздана: §f" + m.coordsString()
                + "§a, " + (int) Math.round(dist) + " м");
    }

    private static void list() {
        List<GPSMarker> list = MeetFroz.manager.snapshot();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (list.isEmpty()) {
            notifyNow("§7Нет активных GPS-меток. Создайте: §f.gps Spawn 100 64 -250");
            return;
        }
        notifyNow("§bАктивные GPS-метки (" + list.size() + ")§7:");
        String curDim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        for (GPSMarker m : list) {
            String dist = "";
            if (mc.player != null && m.dimension.equals(curDim)) {
                dist = " §7— §b" + (int) Math.round(
                        m.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + " м";
            }
            notifyNow(" §8• §f" + m.name + " §7" + m.coordsString() + dist);
        }
        if (!MeetFroz.manager.isEnabled()) {
            notifyNow("§c(отображение выключено — .gps on)");
        }
    }

    private static void usage(String cmd) {
        if (cmd != null) {
            notifyNow("§cИспользование: §f.gps " + cmd);
            return;
        }
        notifyNow("§bMeetFroz GPS §7— команды:");
        notifyNow(" §f.gps <name> <x> <y> <z> §7— создать метку");
        notifyNow(" §f.gps <name> <x> <z> §7— создать (Y у игрока)");
        notifyNow(" §f.gps list §7— список, §f.gps remove <name> §7— удалить");
        notifyNow(" §f.gps clear §7— очистить, §f.gps on/off §7— показ/скрыть");
    }

    private static void notifyNow(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§b[MeetFroz] §r" + msg), false);
        } else {
            MeetFroz.LOGGER.info("{}", msg);
        }
    }
}
