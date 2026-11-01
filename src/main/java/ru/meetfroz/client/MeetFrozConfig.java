package ru.meetfroz.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MeetFrozConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("meetfroz/gui.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MeetFrozConfig INSTANCE = new MeetFrozConfig();

    public boolean labelsEnabled = true;
    public boolean arrowEnabled = true;
    public boolean arrowShowDistance = true;
    public int labelScale = 100;
    public int labelColor = 0x55FFFF;
    public int arrowColor = 0x55FFFF;
    public int arrowScale = 100;
    public String cosmetic = "none";

    public static MeetFrozConfig get() { return INSTANCE; }

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                MeetFrozConfig loaded = GSON.fromJson(Files.readString(FILE), MeetFrozConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
        } catch (Exception e) {
            MeetFroz.LOGGER.error("Не удалось загрузить настройки GUI", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            MeetFroz.LOGGER.error("Не удалось сохранить настройки GUI", e);
        }
    }
}
