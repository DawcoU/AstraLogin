package pl.dawcou.astralogin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Centralny menedżer danych graczy w plikach JSON
//--------------------------------------------------
public class PlayerDataManager {

    private final AstraLogin plugin;
    private final File playersFolder;
    private final Gson gson;

    private final Map<UUID, JsonObject> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.playersFolder = new File(plugin.getDataFolder(), "data/players");

        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    //--------------------------------------------------
    // Pomocnicze metody do obsługi ścieżek z kropkami
    //--------------------------------------------------
    private JsonElement getElementByPath(JsonObject root, String path) {
        return JsonHelper.getElementByPath(root, path);
    }

    //--------------------------------------------------
    // Metody odczytu danych z pliku JSON
    //--------------------------------------------------
    public String getString(UUID uuid, String key) {
        JsonObject root = getOrLoad(uuid);
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    public boolean getBoolean(UUID uuid, String key, boolean defaultValue) {
        JsonObject root = getOrLoad(uuid);
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsBoolean();
        }
        return defaultValue;
    }

    public long getLong(UUID uuid, String key, long defaultValue) {
        JsonObject root = getOrLoad(uuid);
        JsonElement element = getElementByPath(root, key);
        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
            return element.getAsLong();
        }
        return defaultValue;
    }

    public boolean has(UUID uuid, String key) {
        JsonObject root = getOrLoad(uuid);
        JsonElement element = getElementByPath(root, key);
        return element != null && !element.isJsonNull();
    }

    //--------------------------------------------------
    // Metody zapisu i usuwania kluczy
    //--------------------------------------------------
    public void set(UUID uuid, String key, String value) {
        if (value == null) {
            remove(uuid, key);
            return;
        }
        set(uuid, key, new JsonPrimitive(value));
    }

    public void set(UUID uuid, String key, Number value) {
        if (value == null) {
            remove(uuid, key);
            return;
        }
        set(uuid, key, new JsonPrimitive(value));
    }

    public void set(UUID uuid, String key, Boolean value) {
        if (value == null) {
            remove(uuid, key);
            return;
        }
        set(uuid, key, new JsonPrimitive(value));
    }

    public void set(UUID uuid, String key, JsonElement value) {
        JsonObject root = getOrLoad(uuid);
        if (value == null) {
            remove(uuid, key);
            return;
        }

        if (!key.contains(".")) {
            root.add(key, value);
        } else {
            String parentPath = key.substring(0, key.lastIndexOf('.'));
            String finalKey = key.substring(key.lastIndexOf('.') + 1);
            JsonObject parent = JsonHelper.getTargetObject(root, parentPath, true);
            parent.add(finalKey, value);
        }

        saveAsync(uuid, root);
    }

    public void remove(UUID uuid, String key) {
        JsonObject root = getOrLoad(uuid);

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

        saveAsync(uuid, root);
    }

    //--------------------------------------------------
    // Wewnętrzna obsługa cache i I/O
    //--------------------------------------------------
    private JsonObject getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromFile);
    }

    private JsonObject loadFromFile(UUID uuid) {
        File file = new File(playersFolder, uuid.toString() + ".json");
        if (!file.exists()) {
            return new JsonObject();
        }

        try (FileReader reader = new FileReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load player data for " + uuid + ": " + e.getMessage());
        }
        return new JsonObject();
    }

    private synchronized void saveToFile(UUID uuid, JsonObject json) {
        File file = new File(playersFolder, uuid + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data for " + uuid + ": " + e.getMessage());
        }
    }

    private void saveAsync(UUID uuid, JsonObject json) {
        JsonObject copy = json.deepCopy();

        if (!plugin.isEnabled()) {
            saveToFile(uuid, copy);
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            // Jeśli gracz został usunięty z cache w trakcie trwania zadania, anulujemy zapis!
            if (!cache.containsKey(uuid)) {
                return;
            }
            saveToFile(uuid, copy);
        });
    }

    public void saveAll() {
        for (Map.Entry<UUID, JsonObject> entry : cache.entrySet()) {
            saveToFile(entry.getKey(), entry.getValue());
        }
    }

    public void reloadAll() {
        cache.clear();
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    //--------------------------------------------------
    // Pobieranie listy UUID wszystkich graczy z plików
    //--------------------------------------------------
    public java.util.List<UUID> getAllPlayerUUIDs() {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();

        if (!playersFolder.exists() || !playersFolder.isDirectory()) {
            return uuids;
        }

        File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return uuids;

        for (File file : files) {
            String fileName = file.getName();
            String uuidStr = fileName.substring(0, fileName.length() - 5);
            try {
                uuids.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {}
        }

        return uuids;
    }

    public void deletePlayerData(UUID uuid) {
        // 1. Najpierw czyścimy powiązane dane w AccountManagerze
        plugin.getAccountManager().purgeAccountData(uuid);

        // 2. Wyrzucamy z cache, aby żaden późniejszy odczyt nie przywrócił obiektu
        unloadPlayer(uuid);

        // 3. Fizycznie kasujemy plik
        File file = new File(playersFolder, uuid + ".json");
        if (file.exists()) {
            try {
                java.nio.file.Files.delete(file.toPath());
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not delete player data file for " + uuid + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}