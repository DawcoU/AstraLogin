package pl.dawcou.astralogin.file.converters;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class FilesConverter {

    private final AstraLogin plugin;

    public FilesConverter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // Główna metoda, która zarządza wszystkimi konwersjami
    public void runAllMigrations() {
        migratePlayerDataFolder();
        migratePasswordSection();
        migrateInventorySection();
        migrateSpawnSection();
        migrateDataFolders();
    }

    /**
     * Migruje passwords.yml:
     *
     * STARY FORMAT:
     *
     * passwords:
     *   UUID: "$2a$10$..."
     *
     * NOWY FORMAT:
     *
     * passwords:
     *   UUID:
     *     password: "$2a$10$..."
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

        // Migrate old format:
        // passwords:
        //   UUID: "hash"
        ConfigurationSection passwords = config.getConfigurationSection("passwords");

        if (passwords != null) {
            for (String uuid : passwords.getKeys(false)) {
                Object value = passwords.get(uuid);

                if (value instanceof String hash) {
                    passwords.set(uuid, null);
                    passwords.set(uuid + ".password", hash);
                    migrated = true;
                }
            }
        }

        if (migrated) {
            saveConfig(config, passFile);
        }
    }

    /**
     * Migrates inventory_storage.yml -> inventory_data.yml
     * and the old root UUID format into inventory.<UUID>.
     */
    private void migrateInventorySection() {
        File playerDataFolder = new File(plugin.getDataFolder(), "player_data");

        ensureDirectory(playerDataFolder);

        File oldInvFile = new File(playerDataFolder, "inventory_storage.yml");
        File newInvFile = new File(playerDataFolder, "inventory_data.yml");

        if (oldInvFile.exists() && !newInvFile.exists()) {
            try {
                Files.move(oldInvFile.toPath(), newInvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
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
                    inventory.set(key, oldPlayerData);
                    config.set(key, null);
                    migrated = true;
                }
            }
        }

        if (migrated) {
            saveConfig(config, newInvFile);
        }
    }

    /**
     * Migrates:
     *
     * spawns/locations.yml
     *
     * into:
     *
     * global_data/spawns.yml
     * player_data/locations_data.yml
     */
    private void migrateSpawnSection() {
        File oldDir = new File(plugin.getDataFolder(), "spawns");
        File oldFile = new File(oldDir, "locations.yml");

        if (!oldFile.exists()) {
            return;
        }

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
        }
    }

    /**
     * Migrates playerdata -> player_data.
     */
    private void migratePlayerDataFolder() {
        File oldFolder = new File(plugin.getDataFolder(), "playerdata");
        File newFolder = new File(plugin.getDataFolder(), "player_data");

        if (!oldFolder.exists() || newFolder.exists()) {
            return;
        }

        try {
            Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate playerdata -> player_data!");
            e.printStackTrace();
        }
    }

    /**
     * Migrates:
     *
     * global_data/ -> data/global/
     * player_data/ -> data/players/
     */
    private void migrateDataFolders() {
        File oldGlobalFolder = new File(plugin.getDataFolder(), "global_data");
        File oldPlayersFolder = new File(plugin.getDataFolder(), "player_data");

        File dataFolder = new File(plugin.getDataFolder(), "data");
        File newGlobalFolder = new File(dataFolder, "global");
        File newPlayersFolder = new File(dataFolder, "players");

        ensureDirectory(dataFolder);

        migrateFolderContents(oldGlobalFolder, newGlobalFolder);
        migrateFolderContents(oldPlayersFolder, newPlayersFolder);

        deleteEmptyDirectory(oldGlobalFolder);
        deleteEmptyDirectory(oldPlayersFolder);

        deleteEmptyDirectory(dataFolder);
    }

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

            if (target.exists()) {
                continue;
            }

            try {
                Files.move(file.toPath(), target.toPath());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to migrate file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    private void deleteEmptyDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null && files.length == 0) {
            directory.delete();
        }
    }

    /**
     * Creates a directory if it does not exist.
     */
    private void ensureDirectory(File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    /**
     * Saves a configuration file.
     */
    private void saveConfig(FileConfiguration config, File file) {
        synchronized (config) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save file: " + file.getName());
                e.printStackTrace();
            }
        }
    }
}