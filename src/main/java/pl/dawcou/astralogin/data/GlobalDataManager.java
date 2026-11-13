package pl.dawcou.astralogin.data;

import com.google.gson.*;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.JsonHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

//--------------------------------------------------
// Centralny menedżer danych globalnych w pliku JSON
//--------------------------------------------------
public class GlobalDataManager {

    private final AstraLogin plugin;
    private final File globalFile;
    private final Gson gson;

    private JsonObject globalCache;

    private final Set<String> blockedSaves = ConcurrentHashMap.newKeySet();
    private static final String GLOBAL_SAVE_KEY = "global";

    private final Object saveLock = new Object();
    private final AtomicLong saveVersion = new AtomicLong();

    public GlobalDataManager(AstraLogin plugin) {
        this.plugin = plugin;

        File globalDir = new File(plugin.getDataFolder(), "data");
        if (!globalDir.exists()) {
            globalDir.mkdirs();
        }

        this.globalFile = new File(globalDir, "global.json");

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        reload();
    }

    private JsonElement getElementByPath(JsonObject root, String path) {
        return JsonHelper.getElementByPath(root, path);
    }

    //--------------------------------------------------
    // Metody odczytu danych z pliku JSON
    //--------------------------------------------------
    public String getString(String key) {
        return JsonHelper.getString(getOrLoad(), key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return JsonHelper.getBoolean(getOrLoad(), key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return JsonHelper.getInt(getOrLoad(), key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return JsonHelper.getLong(getOrLoad(), key, defaultValue);
    }

    public boolean has(String key) {
        return JsonHelper.has(getOrLoad(), key);
    }

    public JsonObject getJsonObject(String key) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    //--------------------------------------------------
    // Metody zapisu i usuwania kluczy
    //--------------------------------------------------
    public void set(String key, String value) {
        if (value == null) {
            remove(key);
            return;
        }
        set(key, new JsonPrimitive(value));
    }

    public void set(String key, Number value) {
        if (value == null) {
            remove(key);
            return;
        }
        set(key, new JsonPrimitive(value));
    }

    public void set(String key, Boolean value) {
        if (value == null) {
            remove(key);
            return;
        }
        set(key, new JsonPrimitive(value));
    }

    public void set(String key, JsonElement element) {
        synchronized (saveLock) {
            JsonObject root = getOrLoad();
            if (element == null || element.isJsonNull()) {
                removeInternal(root, key);
            } else {
                putInternal(root, key, element);
            }
            saveAsyncInternal(root);
        }
    }

    public void remove(String key) {
        synchronized (saveLock) {
            JsonObject root = getOrLoad();
            removeInternal(root, key);
            saveAsyncInternal(root);
        }
    }

    private void putInternal(JsonObject root, String key, JsonElement value) {
        if (!key.contains(".")) {
            root.add(key, value);
        } else {
            String parentPath = key.substring(0, key.lastIndexOf('.'));
            String finalKey = key.substring(key.lastIndexOf('.') + 1);
            JsonObject parent = JsonHelper.getTargetObject(root, parentPath, true);
            parent.add(finalKey, value);
        }
    }

    private void removeInternal(JsonObject root, String key) {
        if (!key.contains(".")) {
            root.remove(key);
        } else {
            String parentPath = key.substring(0, key.lastIndexOf('.'));
            String finalKey = key.substring(key.lastIndexOf('.') + 1);
            JsonObject parent = JsonHelper.getTargetObject(root, parentPath, false);
            if (parent != null) {
                parent.remove(finalKey);
            }
        }
    }

    private void saveAsyncInternal(JsonObject root) {
        JsonObject copy = root.deepCopy();
        long version = saveVersion.incrementAndGet();

        if (!plugin.isEnabled()) {
            if (version == saveVersion.get()) {
                saveToFile(copy);
            }
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            synchronized (saveLock) {
                if (version != saveVersion.get()) {
                    return;
                }
                saveToFile(copy);
            }
        });
    }

    //--------------------------------------------------
    // Metody do obsługi kluczy ze znakami specjalnymi (np. IP)
    //--------------------------------------------------

    public int getIntExplicit(int defaultValue, String... keys) {
        JsonElement element = getElementExplicit(keys);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsInt();
        }
        return defaultValue;
    }

    public JsonElement getElementExplicit(String... keys) {
        return JsonHelper.getElementExplicit(getOrLoad(), keys);
    }

    public void setExplicit(Object value, String... keys) {
        synchronized (saveLock) {
            JsonObject root = getOrLoad();
            if (JsonHelper.setExplicit(root, value, keys)) {
                saveAsyncInternal(root);
            }
        }
    }

    public void removeExplicit(String... keys) {
        synchronized (saveLock) {
            JsonObject root = getOrLoad();
            if (JsonHelper.removeExplicit(root, keys)) {
                saveAsyncInternal(root);
            }
        }
    }

    //--------------------------------------------------
    // Wewnętrzna obsługa cache i I/O
    //--------------------------------------------------
    private synchronized JsonObject getOrLoad() {
        if (globalCache == null) {
            globalCache = loadFromFile();
        }
        return globalCache;
    }

    private JsonObject loadFromFile() {
        if (!globalFile.exists()) {
            blockedSaves.remove(GLOBAL_SAVE_KEY);
            return new JsonObject();
        }

        try (BufferedReader reader = Files.newBufferedReader(globalFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element != null && element.isJsonObject()) {
                blockedSaves.remove(GLOBAL_SAVE_KEY);
                return element.getAsJsonObject();
            }

            plugin.getLogger().severe(
                    "File '" + globalFile.getName()
                            + "' does not contain a valid JSON Object!"
            );

            return recoverCorruptedFile();

        } catch (JsonParseException e) {
            JsonHelper.logFormattedJsonError(
                    plugin,
                    globalFile.getName(),
                    e
            );

            return recoverCorruptedFile();

        } catch (IOException e) {
            blockedSaves.add(GLOBAL_SAVE_KEY);

            plugin.getLogger().severe(
                    "Could not read '"
                            + globalFile.getName() + "': " + e.getMessage()
            );

            return globalCache != null
                    ? globalCache.deepCopy()
                    : new JsonObject();
        }
    }

    private void saveToFile(JsonObject json) {
        if (blockedSaves.contains(GLOBAL_SAVE_KEY)) {
            plugin.getLogger().warning(
                    "Save skipped! "
                            + "Global data file is unavailable or corrupted."
            );
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(globalFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Could not save global data: " + e.getMessage()
            );
        }
    }

    public void save() {
        synchronized (saveLock) {
            if (globalCache == null) return;

            long version = saveVersion.incrementAndGet();
            JsonObject copy = globalCache.deepCopy();

            if (version != saveVersion.get()) {
                return;
            }

            saveToFile(copy);
        }
    }

    public void reload() {
        synchronized (saveLock) {
            saveVersion.incrementAndGet();
            globalCache = loadFromFile();
        }
    }

    private JsonObject recoverCorruptedFile() {
        JsonObject cachedData = globalCache != null
                ? globalCache.deepCopy()
                : null;

        String baseName = "global";

        File corruptedFile = new File(
                globalFile.getParentFile(),
                globalFile.getName() + ".corrupted"
        );

        int backupNumber = 1;

        while (corruptedFile.exists()) {
            corruptedFile = new File(
                    globalFile.getParentFile(),
                    baseName + "-" + backupNumber + ".json.corrupted"
            );

            backupNumber++;
        }

        try {
            try {
                Files.move(
                        globalFile.toPath(),
                        corruptedFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(globalFile.toPath(), corruptedFile.toPath());
            }

        } catch (IOException e) {
            blockedSaves.add(GLOBAL_SAVE_KEY);

            plugin.getLogger().severe(
                    "CRITICAL: Could not quarantine corrupted file '"
                            + globalFile.getName() + "': " + e.getMessage()
            );

            return cachedData != null ? cachedData : new JsonObject();
        }

        JsonObject recoveredData = cachedData != null
                ? cachedData
                : new JsonObject();

        try (BufferedWriter writer = Files.newBufferedWriter(
                globalFile.toPath(),
                StandardCharsets.UTF_8
        )) {
            gson.toJson(recoveredData, writer);

            blockedSaves.remove(GLOBAL_SAVE_KEY);

            if (cachedData != null) {
                plugin.getLogger().warning(
                        "Corrupted file '"
                                + globalFile.getName()
                                + "' was quarantined. Global data has been restored from cache."
                );
            } else {
                plugin.getLogger().severe(
                        "CRITICAL DATA LOSS WARNING: "
                                + "Corrupted file '" + globalFile.getName()
                                + "' was quarantined, but no cached data was available. "
                                + "A new empty JSON file has been created. "
                                + "Original data is preserved in '"
                                + corruptedFile.getName() + "'."
                );
            }

            return recoveredData;

        } catch (IOException e) {
            blockedSaves.add(GLOBAL_SAVE_KEY);

            plugin.getLogger().severe(
                    "CRITICAL: Could not create replacement file '"
                            + globalFile.getName() + "': " + e.getMessage()
            );

            return recoveredData;
        }
    }
}