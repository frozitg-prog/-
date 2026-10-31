package ru.meetfroz.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Локальный список друзей. Имена окрашиваются зелёным через клиентскую команду
 * scoreboard только когда обычный Minecraft уже показывает игрока/его имя.
 * Класс не раскрывает невидимых игроков и не меняет правила видимости через стены.
 */
public final class FriendsManager {
    private static final String PREFIX = ".friends";
    private static final String TEAM_NAME = "meetfroz_friends";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("meetfroz").resolve("friends.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() { }.getType();
    private static final List<String> FRIENDS = new ArrayList<>();

    private FriendsManager() { }

    public static void register() {
        load();
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String trimmed = message.trim();
            if (!trimmed.startsWith(".")) return true;
            String[] parts = trimmed.split("\\s+");
            if (!parts[0].equalsIgnoreCase(PREFIX)) return true;
            handle(parts);
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(FriendsManager::applyLocalNameColors);
    }

    private static synchronized void load() {
        try {
            if (Files.exists(FILE)) {
                List<String> saved = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), LIST_TYPE);
                FRIENDS.clear();
                if (saved != null) {
                    for (String name : saved) {
                        if (name != null && VALID_NAME.matcher(name).matches() && !contains(name)) {
                            FRIENDS.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            MeetFroz.LOGGER.error("Не удалось загрузить список друзей MeetFroz", e);
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(FRIENDS), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MeetFroz.LOGGER.error("Не удалось сохранить список друзей MeetFroz", e);
        }
    }

    private static void handle(String[] parts) {
        if (parts.length == 1) {
            usage();
            return;
        }
        switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (parts.length != 3 || !VALID_NAME.matcher(parts[2]).matches()) {
                    notifyPlayer("§cИспользование: §f.friends add <player>");
                    return;
                }
                synchronized (FriendsManager.class) {
                    if (contains(parts[2])) {
                        notifyPlayer("§e" + parts[2] + " уже есть в списке друзей.");
                        return;
                    }
                    FRIENDS.add(parts[2]);
                    save();
                }
                notifyPlayer("§a" + parts[2] + " добавлен(а) в локальный список друзей.");
                applyLocalNameColors(MinecraftClient.getInstance());
            }
            case "remove" -> {
                if (parts.length != 3) {
                    notifyPlayer("§cИспользование: §f.friends remove <player>");
                    return;
                }
                String actual;
                synchronized (FriendsManager.class) {
                    actual = findName(parts[2]);
                    boolean removed = actual != null && FRIENDS.remove(actual);
                    if (removed) save();
                }
                if (actual != null) {
                    removeLocalNameColor(actual);
                    notifyPlayer("§a" + parts[2] + " удалён(а) из списка друзей.");
                } else {
                    notifyPlayer("§c" + parts[2] + " не найден(а) в списке друзей.");
                }
            }
            case "list" -> {
                List<String> copy;
                synchronized (FriendsManager.class) {
                    copy = new ArrayList<>(FRIENDS);
                }
                if (copy.isEmpty()) {
                    notifyPlayer("§7Список друзей пуст. Добавить: §f.friends add <player>");
                } else {
                    notifyPlayer("§aДрузья (" + copy.size() + "): §f" + String.join("§7, §f", copy));
                }
            }
            case "clear" -> {
                synchronized (FriendsManager.class) {
                    FRIENDS.clear();
                    save();
                }
                clearLocalNameColors();
                notifyPlayer("§aСписок друзей очищен.");
            }
            default -> usage();
        }
    }

    private static void usage() {
        notifyPlayer("§bMeetFroz Friends §7— .friends add <player>, list, remove <player>, clear");
        notifyPlayer("§7Друзья подсвечиваются зелёным, когда их имя уже отображается обычной игрой.");
    }

    private static synchronized boolean contains(String name) {
        return findName(name) != null;
    }

    private static synchronized String findName(String name) {
        for (String friend : FRIENDS) {
            if (friend.equalsIgnoreCase(name)) return friend;
        }
        return null;
    }

    private static void applyLocalNameColors(MinecraftClient client) {
        if (client.world == null || FRIENDS.isEmpty()) return;
        Scoreboard scoreboard = client.world.getScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addTeam(TEAM_NAME);
            team.setColor(Formatting.GREEN);
        }
        List<String> copy;
        synchronized (FriendsManager.class) {
            copy = new ArrayList<>(FRIENDS);
        }
        for (String name : copy) {
            if (scoreboard.getScoreHolderTeam(name) != team) {
                scoreboard.addScoreHolderToTeam(name, team);
            }
        }
    }

    private static void removeLocalNameColor(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Scoreboard scoreboard = client.world.getScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        String actual = findName(name);
        if (team != null && actual != null) scoreboard.removeScoreHolderFromTeam(actual, team);
    }

    private static void clearLocalNameColors() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Scoreboard scoreboard = client.world.getScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team != null) scoreboard.removeTeam(team);
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§b[MeetFroz] §r" + message), false);
        } else {
            MeetFroz.LOGGER.info("{}", message);
        }
    }

    public static synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(FRIENDS));
    }
}
