package pl.dawcou.astralogin.data;

import com.google.gson.*;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.JsonHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

//--------------------------------------------------
// Centralny menedżer danych graczy w plikach JSON
//--------------------------------------------------
public class PlayerDataManager {

    private final AstraLogin plugin;
    private final File playersFolder;
    private final Gson gson;

    private final Map<UUID, JsonObject> cache = new ConcurrentHashMap<>();
    private final Set<UUID> blockedSaves = ConcurrentHashMap.newKeySet();

    private final Map<UUID, SaveState> saveStates = new ConcurrentHashMap<>();

    private static class SaveState {
        private final Object lock = new Object();
        private final AtomicLong version = new AtomicLong();
    }

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
    // Metody odczytu danych z pliku JSON
    //--------------------------------------------------
    public String getString(UUID uuid, String key) {
        return JsonHelper.getString(getOrLoad(uuid), key);
    }

    public boolean getBoolean(UUID uuid, String key, boolean defaultValue) {
        return JsonHelper.getBoolean(getOrLoad(uuid), key, defaultValue);
    }

    public int getInt(UUID uuid, String key, int defaultValue) {
        return JsonHelper.getInt(getOrLoad(uuid), key, defaultValue);
    }

    public long getLong(UUID uuid, String key, long defaultValue) {
        return JsonHelper.getLong(getOrLoad(uuid), key, defaultValue);
    }

    public boolean has(UUID uuid, String key) {
        return JsonHelper.has(getOrLoad(uuid), key);
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
        SaveState state = getSaveState(uuid);

        synchronized (state.lock) {
            JsonObject root = cache.computeIfAbsent(uuid, this::loadFromFile);

            if (value == null) {
                removeInternal(root, key);
            } else {
                putInternal(root, key, value);
            }

            saveAsyncInternal(uuid, root, state);
        }
    }

    public void remove(UUID uuid, String key) {
        SaveState state = getSaveState(uuid);

        synchronized (state.lock) {
            JsonObject root = cache.computeIfAbsent(uuid, this::loadFromFile);
            removeInternal(root, key);
            saveAsyncInternal(uuid, root, state);
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

    private void saveAsyncInternal(UUID uuid, JsonObject root, SaveState state) {
        JsonObject copy = root.deepCopy();
        long version = state.version.incrementAndGet();

        if (!plugin.isEnabled()) {
            if (state.version.get() == version) {
                saveToFile(uuid, copy);
            }
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            synchronized (state.lock) {
                if (state.version.get() != version) {
                    return;
                }
                if (!cache.containsKey(uuid)) {
                    return;
                }
                saveToFile(uuid, copy);
            }
        });
    }

    //--------------------------------------------------
    // Wewnętrzna obsługa cache i I/O
    //--------------------------------------------------
    private JsonObject getOrLoad(UUID uuid) {
        SaveState state = getSaveState(uuid);

        synchronized (state.lock) {
            return cache.computeIfAbsent(uuid, this::loadFromFile);
        }
    }

    private SaveState getSaveState(UUID uuid) {
        return saveStates.computeIfAbsent(uuid, ignored -> new SaveState());
    }

    private JsonObject loadFromFile(UUID uuid) {
        return loadFromFile(uuid, null);
    }

    private JsonObject loadFromFile(UUID uuid, JsonObject cachedData) {
        File file = new File(playersFolder, uuid + ".json");

        if (!file.exists()) {
            blockedSaves.remove(uuid);
            return new JsonObject();
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element != null && element.isJsonObject()) {
                blockedSaves.remove(uuid);
                return element.getAsJsonObject();
            }

            plugin.getLogger().severe(
                    "File '" + file.getName()
                            + "' does not contain a valid JSON Object!"
            );

            return recoverCorruptedFile(uuid, file, cachedData);

        } catch (JsonParseException e) {
            JsonHelper.logFormattedJsonError(
                    plugin,
                    file.getName(),
                    e
            );

            return recoverCorruptedFile(uuid, file, cachedData);

        } catch (IOException e) {
            blockedSaves.add(uuid);

            plugin.getLogger().severe(
                    "Could not read '"
                            + file.getName() + "': " + e.getMessage()
            );

            return cachedData != null
                    ? cachedData.deepCopy()
                    : new JsonObject();
        }
    }

    private synchronized void saveToFile(UUID uuid, JsonObject json) {
        if (blockedSaves.contains(uuid)) {
            plugin.getLogger().warning(
                    "Save skipped for " + uuid
                            + "! Player data file is corrupted or could not be loaded."
            );
            return;
        }

        File file = new File(playersFolder, uuid + ".json");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Could not save player data for " + uuid + ": " + e.getMessage()
            );
        }
    }

    private void saveAsync(UUID uuid, JsonObject json) {
        JsonObject copy = json.deepCopy();
        SaveState state = getSaveState(uuid);
        long version = state.version.incrementAndGet();

        if (!plugin.isEnabled()) {
            synchronized (state.lock) {
                if (state.version.get() != version) {
                    return;
                }

                saveToFile(uuid, copy);
            }
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            synchronized (state.lock) {
                if (state.version.get() != version) {
                    return;
                }

                if (!cache.containsKey(uuid)) {
                    return;
                }

                saveToFile(uuid, copy);
            }
        });
    }

    public void saveAll() {
        for (Map.Entry<UUID, JsonObject> entry : cache.entrySet()) {
            UUID uuid = entry.getKey();
            SaveState state = getSaveState(uuid);

            synchronized (state.lock) {
                state.version.incrementAndGet();

                JsonObject copy = entry.getValue().deepCopy();
                saveToFile(uuid, copy);
            }
        }
    }

    public void reloadAll() {
        for (Map.Entry<UUID, JsonObject> entry : cache.entrySet()) {
            UUID uuid = entry.getKey();
            SaveState state = getSaveState(uuid);

            synchronized (state.lock) {
                // Invalidate pending asynchronous saves.
                state.version.incrementAndGet();

                // Preserve the current data before reloading.
                JsonObject cachedData = entry.getValue().deepCopy();

                // Reload the file, using cache as a recovery source.
                JsonObject reloadedData = loadFromFile(uuid, cachedData);

                // Replace the cached entry with the reloaded data.
                cache.put(uuid, reloadedData);
            }
        }
    }

    public void unloadPlayer(UUID uuid) {
        SaveState state = getSaveState(uuid);

        synchronized (state.lock) {
            state.version.incrementAndGet();
            cache.remove(uuid);
            saveStates.remove(uuid);
        }
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
        // 1. Remove related account data.
        plugin.getAccountManager().purgeAccountData(uuid);

        SaveState state = getSaveState(uuid);

        synchronized (state.lock) {
            // Invalidate pending saves.
            state.version.incrementAndGet();

            // Remove cached data and save protection state.
            cache.remove(uuid);
            blockedSaves.remove(uuid);

            // 2. Delete the main player data file.
            File file = new File(playersFolder, uuid + ".json");

            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning(
                        "Could not delete player data file for "
                                + uuid + ": " + e.getMessage()
                );
            }

            // 3. Delete all quarantined backups belonging to this player.
            File[] backups = playersFolder.listFiles((dir, name) ->
                    name.equals(uuid + ".json.corrupted")
                            || name.matches(
                            java.util.regex.Pattern.quote(uuid.toString())
                                    + "-\\d+\\.json\\.corrupted"
                    )
            );

            if (backups != null) {
                for (File backup : backups) {
                    try {
                        Files.deleteIfExists(backup.toPath());
                    } catch (IOException e) {
                        plugin.getLogger().warning(
                                "Could not delete corrupted backup '"
                                        + backup.getName() + "': " + e.getMessage()
                        );
                    }
                }
            }
        }
    }

    private JsonObject recoverCorruptedFile(
            UUID uuid,
            File file,
            JsonObject cachedData
    ) {
        blockedSaves.add(uuid);

        String fileName = uuid + ".json";

        File corruptedFile = new File(
                playersFolder,
                fileName + ".corrupted"
        );

        int backupNumber = 1;

        while (corruptedFile.exists()) {
            corruptedFile = new File(
                    playersFolder,
                    uuid + "-" + backupNumber + ".json.corrupted"
            );

            backupNumber++;
        }

        try {
            try {
                Files.move(
                        file.toPath(),
                        corruptedFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(file.toPath(), corruptedFile.toPath());
            }

        } catch (IOException e) {
            plugin.getLogger().severe(
                    "CRITICAL: Could not quarantine corrupted file '"
                            + file.getName() + "': " + e.getMessage()
            );

            return cachedData != null
                    ? cachedData.deepCopy()
                    : new JsonObject();
        }

        JsonObject recoveredData = cachedData != null
                ? cachedData.deepCopy()
                : new JsonObject();

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(recoveredData, writer);

            blockedSaves.remove(uuid);

            if (cachedData != null) {
                plugin.getLogger().warning(
                        "Corrupted file '"
                                + file.getName()
                                + "' was quarantined. Player data has been restored from cache."
                );
            } else {
                plugin.getLogger().severe(
                        "CRITICAL DATA LOSS WARNING: "
                                + "Corrupted file '" + file.getName()
                                + "' was quarantined, but no cached data was available. "
                                + "A new empty JSON file has been created. "
                                + "Original data is preserved in '"
                                + corruptedFile.getName() + "'."
                );
            }

            return recoveredData;

        } catch (IOException e) {
            blockedSaves.add(uuid);

            plugin.getLogger().severe(
                    "CRITICAL: Could not create replacement file '"
                            + file.getName() + "': " + e.getMessage()
            );

            return recoveredData;
        }
    }
}