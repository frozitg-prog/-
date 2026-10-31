package ru.meetfroz.client;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Локальные клиентские команды .qgps — сообщения не отправляются на сервер. */
public final class GPSCommand {

    private static final String PREFIX = ".qgps";

    private GPSCommand() {
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String trimmed = message.trim();
            if (!trimmed.startsWith(".")) return true;
            String[] parts = trimmed.split("\\s+");
            if (!parts[0].toLowerCase(Locale.ROOT).equals(PREFIX)) return true;
            handle(parts);
            return false;
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
                notifyNow(ok ? "§aМетка §f" + name + " §aудалена."
                        : "§cМетка §f" + name + " §cне найдена.");
            }
            case "add" -> {
                if (parts.length < 3) {
                    usage("add <name> <x> <y> <z>");
                    return;
                }
                String[] normalized = new String[parts.length - 1];
                normalized[0] = PREFIX;
                System.arraycopy(parts, 2, normalized, 1, parts.length - 2);
                createMarker(normalized, mc);
            }
            default -> createMarker(parts, mc);
        }
    }

    /** Принимает .qgps <name> <x> <y> <z> и сокращённую форму <name> <x> <z>. */
    private static void createMarker(String[] parts, MinecraftClient mc) {
        if (parts.length != 4 && parts.length != 5) {
            usage("<name> <x> <y> <z> или <name> <x> <z>");
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
            notifyNow("§cКоординаты должны быть числами. Пример: §f.qgps add Spawn 100 64 -250");
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

        GPSMarker marker = created.get();
        double dist = mc.player != null
                ? marker.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                : 0;
        notifyNow("§aGPS-метка §f" + marker.name + " §aсоздана: §f" + marker.coordsString()
                + "§a, " + (int) Math.round(dist) + " м");
    }

    private static void list() {
        List<GPSMarker> markers = MeetFroz.manager.snapshot();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (markers.isEmpty()) {
            notifyNow("§7Нет активных GPS-меток. Создайте: §f.qgps add Spawn 100 64 -250");
            return;
        }

        notifyNow("§bАктивные GPS-метки (" + markers.size() + ")§7:");
        String currentDimension = mc.world != null
                ? mc.world.getRegistryKey().getValue().toString() : "";
        for (GPSMarker marker : markers) {
            String distance = "";
            if (mc.player != null && marker.dimension.equals(currentDimension)) {
                distance = " §7— §b" + (int) Math.round(
                        marker.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + " м";
            }
            notifyNow(" §8• §f" + marker.name + " §7" + marker.coordsString() + distance);
        }
        if (!MeetFroz.manager.isEnabled()) {
            notifyNow("§c(отображение выключено — .qgps on)");
        }
    }

    private static void usage(String command) {
        if (command != null) {
            notifyNow("§cИспользование: §f.qgps " + command);
            return;
        }
        notifyNow("§bMeetFroz GPS §7— команды:");
        notifyNow(" §f.qgps add <name> <x> <y> <z> §7— создать метку");
        notifyNow(" §f.qgps <name> <x> <z> §7— создать (Y у игрока)");
        notifyNow(" §f.qgps list §7— список, §f.qgps remove <name> §7— удалить");
        notifyNow(" §f.qgps clear §7— очистить, §f.qgps on/off §7— показать/скрыть");
    }

    private static void notifyNow(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§b[MeetFroz] §r" + message), false);
        } else {
            MeetFroz.LOGGER.info("{}", message);
        }
    }
}
