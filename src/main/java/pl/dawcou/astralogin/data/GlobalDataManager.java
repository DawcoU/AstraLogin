package pl.dawcou.astralogin.data;

import com.google.gson.*;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

//--------------------------------------------------
// Centralny menedżer danych globalnych w pliku JSON
//--------------------------------------------------
public class GlobalDataManager {

    private final AstraLogin plugin;
    private final File globalFile;
    private final Gson gson;

    private JsonObject globalCache;

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

    //--------------------------------------------------
    // Pomocnicze metody do obsługi ścieżek z kropkami
    //--------------------------------------------------
    private JsonObject getTargetObject(JsonObject root, String path, boolean createIfMissing) {
        return JsonHelper.getTargetObject(root, path, createIfMissing);
    }

    private String getFinalKey(String path) {
        return JsonHelper.getFinalKey(path);
    }

    private JsonElement getElementByPath(JsonObject root, String path) {
        return JsonHelper.getElementByPath(root, path);
    }

    //--------------------------------------------------
    // Metody odczytu danych z pliku JSON
    //--------------------------------------------------
    public String getString(String key) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsBoolean();
        }
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsLong();
        }
        return defaultValue;
    }

    public JsonObject getJsonObject(String key) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    public boolean has(String key) {
        JsonObject root = getOrLoad();
        JsonElement element = getElementByPath(root, key);
        return element != null && !element.isJsonNull();
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
        JsonObject root = getOrLoad();
        if (element == null || element.isJsonNull()) {
            remove(key);
            return;
        }

        if (!key.contains(".")) {
            root.add(key, element);
        } else {
            String parentPath = key.substring(0, key.lastIndexOf('.'));
            String finalKey = key.substring(key.lastIndexOf('.') + 1);
            JsonObject parent = JsonHelper.getTargetObject(root, parentPath, true);
            parent.add(finalKey, element);
        }

        saveAsync(root);
    }

    public void remove(String key) {
        JsonObject root = getOrLoad();

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

        saveAsync(root);
    }

    //--------------------------------------------------
    // Metody do obsługi kluczy ze znakami specjalnymi (np. IP)
    //--------------------------------------------------

    // Pobieranie liczby int dla ścieżek typu: "ip_trust", "127.0.0.1", "score"
    public int getIntExplicit(int defaultValue, String... keys) {
        JsonElement element = getElementExplicit(keys);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsInt();
        }
        return defaultValue;
    }

    // Pobieranie surowego JsonElement bez splitowania po kropkach
    public JsonElement getElementExplicit(String... keys) {
        return JsonHelper.getElementExplicit(getOrLoad(), keys);
    }

    // Zapisywanie dowolnej wartości (Number, Boolean, String, JsonElement) bez splitowania
    public void setExplicit(Object value, String... keys) {
        JsonObject root = getOrLoad();
        if (JsonHelper.setExplicit(root, value, keys)) {
            saveAsync(root);
        }
    }

    // Usuwanie klucza bez splitowania
    public void removeExplicit(String... keys) {
        JsonObject root = getOrLoad();
        if (JsonHelper.removeExplicit(root, keys)) {
            saveAsync(root);
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
            return new JsonObject();
        }

        try (FileReader reader = new FileReader(globalFile)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load global data: " + e.getMessage());
        }
        return new JsonObject();
    }

    private synchronized void saveToFile(JsonObject json) {
        try (FileWriter writer = new FileWriter(globalFile)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save global data: " + e.getMessage());
        }
    }

    private void saveAsync(JsonObject json) {
        JsonObject copy = json.deepCopy();

        if (!plugin.isEnabled()) {
            saveToFile(copy);
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> saveToFile(copy));
    }

    public void save() {
        if (globalCache == null) return;
        saveToFile(globalCache);
    }

    public synchronized void reload() {
        globalCache = loadFromFile();
    }
}