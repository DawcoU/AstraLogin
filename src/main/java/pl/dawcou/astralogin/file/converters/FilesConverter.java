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
        migratePlayerDataFolder();
        migratePasswordSection();
        migrateInventorySection();
        migrateSpawnSection();
        migrateDataFolders();
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
     * Migrates playerdata folder -> player_data.
     */
    private void migratePlayerDataFolder() {
        File oldFolder = new File(plugin.getDataFolder(), "playerdata");
        File newFolder = new File(plugin.getDataFolder(), "player_data");

        if (!oldFolder.exists() || newFolder.exists()) {
            return;
        }

        plugin.getNoticeManager().sendMigrationNotice(oldFolder.getName(), newFolder.getName());

        try {
            Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getNoticeManager().sendSuccessMigrationNotice(oldFolder.getName());
        } catch (IOException e) {
            plugin.getNoticeManager().sendErrorMigrationNotice("move " + oldFolder.getName() + " -> " + newFolder.getName());
            plugin.getLogger().severe("Failed to migrate playerdata -> player_data!");
            e.printStackTrace();
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

        // 4. Check playerdata folder
        if (new File(dataFolder, "playerdata").exists() && !new File(dataFolder, "player_data").exists()) {
            return true;
        }

        // 5. Check global_data or player_data folders
        return new File(dataFolder, "global_data").exists() || new File(dataFolder, "player_data").exists();
    }
}