package ru.meetfroz.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Управление всеми GPS-метками: создание, удаление, включение/выключение,
 * список и сохранение на диск (config/meetfroz/markers.json).
 *
 * Хранит также счётчик авто-меток из чата («Chat GPS N») и общий флаг
 * видимости. Все метки живут до явного удаления или очистки.
 */
public final class GPSManager {

    private static final class Store {
        boolean enabled = true;
        int chatCounter = 0;
        List<GPSMarker> markers = new ArrayList<>();
    }

    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("meetfroz");
    private final Path file = dir.resolve("markers.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Object lock = new Object();
    private boolean enabled = true;
    private int chatCounter = 0;
    private final List<GPSMarker> markers = new ArrayList<>();

    // ------------------------------------------------------------------ load/save

    public void load() {
        synchronized (lock) {
            try {
                Files.createDirectories(dir);
                if (Files.exists(file)) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    Store store = gson.fromJson(json, new TypeToken<Store>() {
                    }.getType());
                    if (store != null) {
                        enabled = store.enabled;
                        chatCounter = Math.max(0, store.chatCounter);
                        markers.clear();
                        if (store.markers != null) {
                            for (GPSMarker m : store.markers) {
                                if (m != null && m.name != null) markers.add(m);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                MeetFroz.LOGGER.error("Не удалось загрузить метки MeetFroz", e);
                markers.clear();
                enabled = true;
            }
        }
    }

    public void save() {
        synchronized (lock) {
            try {
                Files.createDirectories(dir);
                Store store = new Store();
                store.enabled = enabled;
                store.chatCounter = chatCounter;
                store.markers = new ArrayList<>(markers);
                String json = gson.toJson(store);
                Path tmp = dir.resolve("markers.json.tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                MeetFroz.LOGGER.error("Не удалось сохранить метки MeetFroz", e);
            }
        }
    }

    // ------------------------------------------------------------------ flags

    public boolean isEnabled() {
        synchronized (lock) {
            return enabled;
        }
    }

    public void setEnabled(boolean value) {
        synchronized (lock) {
            enabled = value;
        }
        save();
    }

    // ------------------------------------------------------------------ query

    /** Неизменяемая копия активных меток для рендера. */
    public List<GPSMarker> snapshot() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(markers));
        }
    }

    public int count() {
        synchronized (lock) {
            return markers.size();
        }
    }

    public Optional<GPSMarker> find(String name) {
        synchronized (lock) {
            for (GPSMarker m : markers) {
                if (m.name.equalsIgnoreCase(name)) return Optional.of(m);
            }
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ mutate

    /**
     * Добавляет метку. Если метка с такими же координатами (в том же измерении)
     * уже существует — не создаёт дубликат и возвращает пустой Optional.
     * Если метка с таким именем есть — обновляет её координаты.
     *
     * @return созданный/обновлённый маркер либо пусто при дубликате координат.
     */
    public Optional<GPSMarker> add(String name, double x, double y, double z, String dimension) {
        synchronized (lock) {
            for (GPSMarker m : markers) {
                if (m.dimension.equals(dimension) && m.sameCoordsAt(x, y, z)) {
                    return Optional.empty(); // дубликат координат — не добавляем
                }
            }
            for (GPSMarker m : markers) {
                if (m.name.equalsIgnoreCase(name)) {
                    m.x = x;
                    m.y = y;
                    m.z = z;
                    m.dimension = dimension;
                    m.createdAt = System.currentTimeMillis();
                    return Optional.of(m);
                }
            }
            GPSMarker created = new GPSMarker(name, x, y, z, dimension);
            markers.add(created);
            return Optional.of(created);
        }
    }

    /**
     * Создаёт авто-метку из чата с порядковым названием — просто «1», «2», «3»…
     *
     * Берётся наименьший свободный номер: он не должен совпадать (без учёта
     * регистра) ни с одним уже существующим названием — ни авто-метки, ни
     * пользовательской. Удалённые метки не переименовывают остальные, а освободившийся
     * номер может быть взят следующей новой меткой.
     */
    public Optional<GPSMarker> addFromChat(double x, double y, double z, String dimension) {
        String name;
        synchronized (lock) {
            name = nextFreeNumber();
            chatCounter++; // статистика совместимости со старыми сохранениями
        }
        return add(name, x, y, z, dimension);
    }

    /** Наименьший свободный порядковый номер («1», «2», …) среди всех меток. */
    private String nextFreeNumber() {
        for (int n = 1; n < 1_000_000; n++) {
            String s = Integer.toString(n);
            boolean used = false;
            for (GPSMarker m : markers) {
                if (m.name != null && m.name.equalsIgnoreCase(s)) {
                    used = true;
                    break;
                }
            }
            if (!used) return s;
        }
        return Long.toString(System.currentTimeMillis() % 1_000_000_000L);
    }

    public boolean remove(String name) {
        synchronized (lock) {
            boolean removed = markers.removeIf(m -> m.name.equalsIgnoreCase(name));
            return removed;
        }
    }

    public void clear() {
        synchronized (lock) {
            markers.clear();
        }
    }
}
