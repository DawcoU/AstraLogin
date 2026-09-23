package pl.dawcou.astralogin.file.converters;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/*
 * Manages file-system and YAML data layout conversions across plugin updates.
 */
public class FilesConverter {

    private final AstraLogin plugin;

    public FilesConverter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    /*
     * Executes all file and folder data migrations.
     */
    public void runAllMigrations() {
        migratePasswordSection();
        migrateInventorySection();
        migrateSpawnSection();
        migrateDataFolders();
        migrateYamlToJson();
    }

    /*
     * Migrates passwords.yml from legacy string value format to nested object format.
     */
    private void migratePasswordSection() {
        File playerDataFolder = new File(plugin.getDataFolder(), "player_data");
        File passFile = new File(playerDataFolder, "passwords.yml");

        if (!passFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(passFile);
        boolean migrated = false;

        // Migrate old "players" section
        if (config.isConfigurationSection("players")) {
            plugin.getNoticeManager().sendMigrationNotice("passwords.yml (players)", "passwords.yml (passwords)");

            ConfigurationSection oldSection = config.getConfigurationSection("players");
            ConfigurationSection newSection = config.getConfigurationSection("passwords");

            if (newSection == null) {
                newSection = config.createSection("passwords");
            }

            if (oldSection != null) {
                for (String uuid : oldSection.getKeys(false)) {
                    if (!newSection.contains(uuid)) {
                        newSection.set(uuid, oldSection.get(uuid));
                    }
                }
            }

            config.set("players", null);
            migrated = true;
        }

        // Migrate old format: passwords.UUID = "hash" -> passwords.UUID.password = "hash"
        ConfigurationSection passwords = config.getConfigurationSection("passwords");

        if (passwords != null) {
            for (String uuid : passwords.getKeys(false)) {
                Object value = passwords.get(uuid);

                if (value instanceof String hash) {
                    if (!migrated) {
                        plugin.getNoticeManager().sendMigrationNotice("passwords.yml (String hash)", "passwords.yml (Nested object)");
                    }
                    passwords.set(uuid, null);
                    passwords.set(uuid + ".password", hash);
                    migrated = true;
                }
            }
        }

        if (migrated) {
            saveConfig(config, passFile);
            plugin.getNoticeManager().sendSuccessMigrationNotice("passwords.yml");
        }
    }

    /*
     * Migrates inventory_storage.yml -> inventory_data.yml
     * and wraps raw root UUID keys inside an inventory.<UUID> section.
     */
    private void migrateInventorySection() {
        File playerDataFolder = new File(plugin.getDataFolder(), "player_data");
        if (!playerDataFolder.exists()) {
            return;
        }

        File oldInvFile = new File(playerDataFolder, "inventory_storage.yml");
        File newInvFile = new File(playerDataFolder, "inventory_data.yml");

        if (oldInvFile.exists() && !newInvFile.exists()) {
            plugin.getNoticeManager().sendMigrationNotice(oldInvFile.getName(), newInvFile.getName());
            try {
                Files.move(oldInvFile.toPath(), newInvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getNoticeManager().sendSuccessMigrationNotice(oldInvFile.getName());
            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice("rename " + oldInvFile.getName() + " -> " + newInvFile.getName());
                plugin.getLogger().severe("Failed to rename inventory_storage.yml to inventory_data.yml!");
                e.printStackTrace();
                return;
            }
        }

        if (!newInvFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(newInvFile);
        Set<String> rootKeys = config.getKeys(false);

        boolean migrated = false;

        ConfigurationSection inventory = config.getConfigurationSection("inventory");

        if (inventory == null) {
            inventory = config.createSection("inventory");
        }

        for (String key : rootKeys) {
            if (key.equalsIgnoreCase("inventory")) {
                continue;
            }

            if (config.isConfigurationSection(key)) {
                ConfigurationSection oldPlayerData = config.getConfigurationSection(key);

                if (oldPlayerData != null) {
                    if (!migrated) {
                        plugin.getNoticeManager().sendMigrationNotice("inventory_data.yml (Root keys)", "inventory_data.yml (inventory.<UUID>)");
                    }
                    inventory.set(key, oldPlayerData);
                    config.set(key, null);
                    migrated = true;
                }
            }
        }

        if (migrated) {
            saveConfig(config, newInvFile);
            plugin.getNoticeManager().sendSuccessMigrationNotice("inventory_data.yml");
        }
    }

    /*
     * Migrates spawns/locations.yml into global_data/spawns.yml and player_data/locations_data.yml.
     */
    private void migrateSpawnSection() {
        File oldDir = new File(plugin.getDataFolder(), "spawns");
        File oldFile = new File(oldDir, "locations.yml");

        if (!oldFile.exists()) {
            return;
        }

        plugin.getNoticeManager().sendMigrationNotice("spawns/locations.yml", "global_data/spawns.yml & player_data/locations_data.yml");

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);

        File globalDir = new File(plugin.getDataFolder(), "global_data");
        File playerDir = new File(plugin.getDataFolder(), "player_data");

        ensureDirectory(globalDir);
        ensureDirectory(playerDir);

        File newSpawnsFile = new File(globalDir, "spawns.yml");
        File newPlayerDataFile = new File(playerDir, "locations_data.yml");

        FileConfiguration newSpawnsConfig = YamlConfiguration.loadConfiguration(newSpawnsFile);
        FileConfiguration newPlayerDataConfig = YamlConfiguration.loadConfiguration(newPlayerDataFile);

        boolean changedSpawns = false;
        boolean changedPlayers = false;

        ConfigurationSection spawns = oldConfig.getConfigurationSection("spawns");

        if (spawns != null) {
            newSpawnsConfig.set("spawns", spawns);
            changedSpawns = true;
        }

        ConfigurationSection lastLocations = oldConfig.getConfigurationSection("last_locations");

        if (lastLocations != null) {
            newPlayerDataConfig.set("last_locations", lastLocations);
            changedPlayers = true;
        }

        if (changedSpawns) {
            saveConfig(newSpawnsConfig, newSpawnsFile);
        }

        if (changedPlayers) {
            saveConfig(newPlayerDataConfig, newPlayerDataFile);
        }

        if (oldFile.delete()) {
            File[] files = oldDir.listFiles();

            if (files == null || files.length == 0) {
                oldDir.delete();
            }
            plugin.getNoticeManager().sendSuccessMigrationNotice("spawns/locations.yml");
        } else {
            plugin.getNoticeManager().sendErrorMigrationNotice("delete " + oldFile.getName());
        }
    }

    /*
     * Migrates global_data/ -> data/global/ and player_data/ -> data/players/.
     */
    private void migrateDataFolders() {
        File oldGlobalFolder = new File(plugin.getDataFolder(), "global_data");
        File oldPlayersFolder = new File(plugin.getDataFolder(), "player_data");

        File dataFolder = new File(plugin.getDataFolder(), "data");
        File newGlobalFolder = new File(dataFolder, "global");
        File newPlayersFolder = new File(dataFolder, "players");

        if (oldGlobalFolder.exists()) {
            ensureDirectory(dataFolder);
            plugin.getNoticeManager().sendMigrationNotice("global_data", "data/global");
            migrateFolderContents(oldGlobalFolder, newGlobalFolder);

            // BARDZO WAŻNE: Usuwamy cały stary folder wraz z zawartością/pustymi podfolderami
            deleteFolderRecursively(oldGlobalFolder);

            plugin.getNoticeManager().sendSuccessMigrationNotice("global_data");
        }

        if (oldPlayersFolder.exists()) {
            ensureDirectory(dataFolder);
            plugin.getNoticeManager().sendMigrationNotice("player_data", "data/players");
            migrateFolderContents(oldPlayersFolder, newPlayersFolder);

            // BARDZO WAŻNE: Usuwamy cały stary folder
            deleteFolderRecursively(oldPlayersFolder);

            plugin.getNoticeManager().sendSuccessMigrationNotice("player_data");
        }
    }

    /*
     * Migrates legacy YAML files into player-specific JSON files and single global.json.
     */
    private void migrateYamlToJson() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playersFolder = new File(dataFolder, "players");
        File globalFolder = new File(dataFolder, "global");

        File passwordsYml = new File(playersFolder, "passwords.yml");
        File ipsYml = new File(playersFolder, "ips.yml");
        File locationsYml = new File(playersFolder, "locations_data.yml");
        File sessionsYml = new File(playersFolder, "session_data.yml");
        File inventoryDataYml = new File(playersFolder, "inventory_data.yml");

        File accountsYml = new File(globalFolder, "accounts.yml");
        File ipBansYml = new File(globalFolder, "ip_bans.yml");
        File ipTrustYml = new File(globalFolder, "ip-trust.yml");
        File spawnsYml = new File(globalFolder, "spawns.yml");

        boolean anyYamlExists = passwordsYml.exists() || ipsYml.exists() || locationsYml.exists()
                || accountsYml.exists() || ipBansYml.exists() || ipTrustYml.exists() || spawnsYml.exists();

        if (!anyYamlExists) {
            return;
        }

        plugin.getNoticeManager().sendMigrationNotice("YAML files", "JSON format (data/players/<UUID>.json & data/global.json)");

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        java.util.Map<String, com.google.gson.JsonObject> playerJsonMap = new java.util.HashMap<>();

        // --------------------------------------------------
        // 1. Migracja passwords.yml -> auth.password
        // --------------------------------------------------
        if (passwordsYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(passwordsYml);
            ConfigurationSection passwords = config.getConfigurationSection("passwords");
            if (passwords != null) {
                for (String uuid : passwords.getKeys(false)) {
                    String pass = passwords.getString(uuid + ".password");
                    if (pass == null && passwords.isString(uuid)) {
                        pass = passwords.getString(uuid);
                    }
                    if (pass != null) {
                        com.google.gson.JsonObject playerJson = getOrCreatePlayerJson(playerJsonMap, playersFolder, uuid, gson);
                        com.google.gson.JsonObject auth = getOrCreateSubObject(playerJson, "auth");
                        auth.addProperty("password", pass);
                    }
                }
            }
        }

        // --------------------------------------------------
        // 2. Migracja ips.yml -> auth.last-ip
        // --------------------------------------------------
        if (ipsYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(ipsYml);
            ConfigurationSection ips = config.getConfigurationSection("ips");
            if (ips != null) {
                for (String uuid : ips.getKeys(false)) {
                    String ip = ips.getString(uuid);
                    if (ip != null) {
                        com.google.gson.JsonObject playerJson = getOrCreatePlayerJson(playerJsonMap, playersFolder, uuid, gson);
                        com.google.gson.JsonObject auth = getOrCreateSubObject(playerJson, "auth");
                        auth.addProperty("last-ip", ip);
                    }
                }
            }
        }

        // --------------------------------------------------
        // 3. Migracja locations_data.yml -> location
        // --------------------------------------------------
        if (locationsYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(locationsYml);
            ConfigurationSection locations = config.getConfigurationSection("last_locations");
            if (locations != null) {
                for (String uuid : locations.getKeys(false)) {
                    ConfigurationSection locSec = locations.getConfigurationSection(uuid);
                    if (locSec != null) {
                        com.google.gson.JsonObject playerJson = getOrCreatePlayerJson(playerJsonMap, playersFolder, uuid, gson);
                        com.google.gson.JsonObject locObj = new com.google.gson.JsonObject();
                        locObj.addProperty("world", locSec.getString("world"));
                        locObj.addProperty("x", locSec.getDouble("x"));
                        locObj.addProperty("y", locSec.getDouble("y"));
                        locObj.addProperty("z", locSec.getDouble("z"));
                        locObj.addProperty("yaw", (float) locSec.getDouble("yaw"));
                        locObj.addProperty("pitch", (float) locSec.getDouble("pitch"));

                        playerJson.add("location", locObj);
                    }
                }
            }
        }

        // --------------------------------------------------
        // 4. Migracja accounts.yml -> dane gracza (name, register-date itp.)
        // --------------------------------------------------
        if (accountsYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(accountsYml);
            ConfigurationSection accounts = config.getConfigurationSection("accounts");
            if (accounts != null) {
                for (String uuid : accounts.getKeys(false)) {
                    ConfigurationSection accSec = accounts.getConfigurationSection(uuid);
                    if (accSec != null) {
                        com.google.gson.JsonObject playerJson = getOrCreatePlayerJson(playerJsonMap, playersFolder, uuid, gson);

                        // Tworzymy lub pobieramy sekcję "account" wewnątrz pliku gracza
                        com.google.gson.JsonObject accountSection;
                        if (playerJson.has("account") && playerJson.get("account").isJsonObject()) {
                            accountSection = playerJson.getAsJsonObject("account");
                        } else {
                            accountSection = new com.google.gson.JsonObject();
                            playerJson.add("account", accountSection);
                        }

                        if (accSec.contains("last-known-name")) {
                            accountSection.addProperty("name", accSec.getString("last-known-name"));
                        }
                        if (accSec.contains("register-date")) {
                            accountSection.addProperty("register-date", accSec.getString("register-date"));
                        }
                        if (accSec.contains("last-login-date")) {
                            accountSection.addProperty("last-login-date", accSec.getString("last-login-date"));
                        }
                        if (accSec.contains("last-security-reminder-timestamp")) {
                            accountSection.addProperty("last-security-reminder-timestamp", accSec.getLong("last-security-reminder-timestamp"));
                        }
                    }
                }
            }
        }

        // Zapis poszczególnych plików graczy <UUID>.json
        ensureDirectory(playersFolder);
        for (java.util.Map.Entry<String, com.google.gson.JsonObject> entry : playerJsonMap.entrySet()) {
            File targetFile = new File(playersFolder, entry.getKey() + ".json");
            try (java.io.FileWriter writer = new java.io.FileWriter(targetFile)) {
                gson.toJson(entry.getValue(), writer);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save player JSON during migration: " + entry.getKey());
                e.printStackTrace();
            }
        }

        // --------------------------------------------------
        // 5. Migracja danych globalnych do data/global.json
        // --------------------------------------------------
        File globalJsonFile = new File(dataFolder, "global.json");
        com.google.gson.JsonObject globalRoot = new com.google.gson.JsonObject();

        if (globalJsonFile.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(globalJsonFile)) {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    globalRoot = parsed.getAsJsonObject();
                }
            } catch (IOException ignored) {}
        }

        // 5a. Spawny (spawns.yml)
        if (spawnsYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(spawnsYml);
            ConfigurationSection spawnsSec = config.getConfigurationSection("spawns");
            if (spawnsSec != null) {
                com.google.gson.JsonObject spawnsObj = new com.google.gson.JsonObject();
                for (String key : spawnsSec.getKeys(false)) {
                    ConfigurationSection s = spawnsSec.getConfigurationSection(key);
                    if (s != null) {
                        com.google.gson.JsonObject singleSpawn = new com.google.gson.JsonObject();
                        singleSpawn.addProperty("world", s.getString("world"));
                        singleSpawn.addProperty("x", s.getDouble("x"));
                        singleSpawn.addProperty("y", s.getDouble("y"));
                        singleSpawn.addProperty("z", s.getDouble("z"));
                        singleSpawn.addProperty("yaw", (float) s.getDouble("yaw"));
                        singleSpawn.addProperty("pitch", (float) s.getDouble("pitch"));

                        spawnsObj.add(key, singleSpawn);
                    }
                }
                globalRoot.add("spawns", spawnsObj);
            }
        }

        // 5b. IP Bans (ip_bans.yml)
        if (ipBansYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(ipBansYml);
            com.google.gson.JsonObject bansObj = new com.google.gson.JsonObject();

            if (config.isList("bypasses")) {
                com.google.gson.JsonArray bypassesArray = new com.google.gson.JsonArray();
                for (String bypass : config.getStringList("bypasses")) {
                    bypassesArray.add(bypass);
                }
                bansObj.add("bypasses", bypassesArray);
            }

            if (config.isConfigurationSection("bans")) {
                ConfigurationSection bansSec = config.getConfigurationSection("bans");
                if (bansSec != null) {
                    com.google.gson.JsonObject bansList = new com.google.gson.JsonObject();
                    for (String key : bansSec.getKeys(false)) {
                        ConfigurationSection banItem = bansSec.getConfigurationSection(key);
                        if (banItem != null) {
                            String rawIp = banItem.getString("ip");
                            // Jeśli brak pola "ip", przywracamy kropki ze skompresowanego klucza (np. 192_168_1_1)
                            String cleanIp = (rawIp != null) ? rawIp : key.replace("_", ".");

                            long expire = banItem.getLong("expire");
                            String reason = banItem.getString("reason", "UNKNOWN");
                            String uuid = banItem.getString("uuid", "UNKNOWN");

                            // Pomijamy wygasłe bany już na etapie migracji
                            if (expire > System.currentTimeMillis()) {
                                com.google.gson.JsonObject b = new com.google.gson.JsonObject();
                                b.addProperty("ip", cleanIp);
                                b.addProperty("expire", expire);
                                b.addProperty("reason", reason);
                                b.addProperty("uuid", uuid);

                                bansList.add(cleanIp, b);
                            }
                        }
                    }
                    bansObj.add("bans", bansList);
                }
            }
            globalRoot.add("ip_bans", bansObj);
        }

        // 5c. IP Trust (ip_trust.yml) -> zamiana podłóg w IP na kropki
        if (ipTrustYml.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(ipTrustYml);
            ConfigurationSection ipsSec = config.getConfigurationSection("ips");
            if (ipsSec != null) {
                com.google.gson.JsonObject trustObj = new com.google.gson.JsonObject();
                for (String rawIpKey : ipsSec.getKeys(false)) {
                    String cleanIpKey = rawIpKey.replace("_", ".");
                    ConfigurationSection ipData = ipsSec.getConfigurationSection(rawIpKey);
                    if (ipData != null) {
                        com.google.gson.JsonObject scoreObj = new com.google.gson.JsonObject();
                        if (ipData.contains("score")) {
                            scoreObj.addProperty("score", ipData.getInt("score"));
                        }
                        trustObj.add(cleanIpKey, scoreObj);
                    }
                }
                globalRoot.add("ip_trust", trustObj);
            }
        }

        // Zapis do data/global.json
        try (java.io.FileWriter writer = new java.io.FileWriter(globalJsonFile)) {
            gson.toJson(globalRoot, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save global.json during migration!");
            e.printStackTrace();
        }

        // Czyszczenie i usuwanie starych plików YAML
        passwordsYml.delete();
        ipsYml.delete();
        locationsYml.delete();
        accountsYml.delete();
        ipBansYml.delete();
        ipTrustYml.delete();
        spawnsYml.delete();
        sessionsYml.delete();
        inventoryDataYml.delete();

        // Usuwanie starych katalogów jeśli są puste
        deleteEmptyDirectory(globalFolder);

        plugin.getNoticeManager().sendSuccessMigrationNotice("YAML -> JSON Conversion");
    }

    private com.google.gson.JsonObject getOrCreatePlayerJson(
            java.util.Map<String, com.google.gson.JsonObject> map, File playersFolder, String uuid, com.google.gson.Gson gson) {

        if (map.containsKey(uuid)) {
            return map.get(uuid);
        }

        File existingFile = new File(playersFolder, uuid + ".json");
        if (existingFile.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(existingFile)) {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    com.google.gson.JsonObject obj = parsed.getAsJsonObject();
                    map.put(uuid, obj);
                    return obj;
                }
            } catch (IOException ignored) {}
        }

        com.google.gson.JsonObject newObj = new com.google.gson.JsonObject();
        map.put(uuid, newObj);
        return newObj;
    }

    private com.google.gson.JsonObject getOrCreateSubObject(com.google.gson.JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        com.google.gson.JsonObject child = new com.google.gson.JsonObject();
        parent.add(key, child);
        return child;
    }

    /*
     * Recursively transfers files from old directory to new directory.
     */
    private void migrateFolderContents(File oldFolder, File newFolder) {
        if (!oldFolder.exists()) {
            return;
        }

        ensureDirectory(newFolder);

        File[] files = oldFolder.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            File target = new File(newFolder, file.getName());

            if (file.isDirectory()) {
                migrateFolderContents(file, target);
                deleteEmptyDirectory(file);
                continue;
            }

            try {
                Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice("move " + file.getName());
                plugin.getLogger().severe("Failed to migrate file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    /*
     * Removes empty directory from disk.
     */
    private void deleteEmptyDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null && files.length == 0) {
            directory.delete();
        }
    }

    /*
     * Ensures target directory exists.
     */
    private void ensureDirectory(File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    // Helper method to completely remove a folder from disk
    private void deleteFolderRecursively(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolderRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        folder.delete(); // Deletes the folder itself after deleting the contents
    }

    /*
     * Thread-safe saving of configuration files.
     */
    private void saveConfig(FileConfiguration config, File file) {
        synchronized (config) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice("save " + file.getName());
                plugin.getLogger().severe("Failed to save file: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    /*
     * Checks if any file/folder migration is needed before executing.
     */
    public boolean needsMigration() {
        File dataFolder = plugin.getDataFolder();

        // 1. Check passwords.yml
        File passFile = new File(dataFolder, "player_data/passwords.yml");
        if (passFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(passFile);
            if (config.isConfigurationSection("players")) {
                return true;
            }
            ConfigurationSection passwords = config.getConfigurationSection("passwords");
            if (passwords != null) {
                for (String uuid : passwords.getKeys(false)) {
                    if (passwords.get(uuid) instanceof String) {
                        return true;
                    }
                }
            }
        }

        // 2. Check inventory storage
        File playerDataFolder = new File(dataFolder, "player_data");
        if (playerDataFolder.exists()) {
            File oldInvFile = new File(playerDataFolder, "inventory_storage.yml");
            File newInvFile = new File(playerDataFolder, "inventory_data.yml");
            if (oldInvFile.exists() && !newInvFile.exists()) {
                return true;
            }
            if (newInvFile.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(newInvFile);
                for (String key : config.getKeys(false)) {
                    if (!key.equalsIgnoreCase("inventory") && config.isConfigurationSection(key)) {
                        return true;
                    }
                }
            }
        }

        // 3. Check spawns/locations.yml
        if (new File(dataFolder, "spawns/locations.yml").exists()) {
            return true;
        }

        // 4. Check global_data or player_data folders
        if (new File(dataFolder, "global_data").exists() || new File(dataFolder, "player_data").exists()) {
            return true;
        }

        // 5. Check if old YAML files exist that need JSON conversion
        File playersDir = new File(dataFolder, "data/players");
        File globalDir = new File(dataFolder, "data/global");

        return new File(playersDir, "passwords.yml").exists() ||
                new File(playersDir, "ips.yml").exists() ||
                new File(playersDir, "locations_data.yml").exists() ||
                new File(globalDir, "accounts.yml").exists() ||
                new File(globalDir, "ip_bans.yml").exists() ||
                new File(globalDir, "ip_trust.yml").exists() ||
                new File(globalDir, "spawns.yml").exists();
    }
}