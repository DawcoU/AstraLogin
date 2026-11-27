package pl.dawcou.astralogin.file.converters.file.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class InventorySectionFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File oldFile;
    private final File newFile;

    public InventorySectionFileMigration(AstraLogin plugin) {
        this.plugin = plugin;
        File playerFolder = new File(plugin.getDataFolder(), "player_data");
        this.oldFile = new File(playerFolder, "inventory_storage.yml");
        this.newFile = new File(playerFolder, "inventory_data.yml");
    }

    @Override
    public boolean needsMigration() {
        if (oldFile.exists() && !newFile.exists()) {
            return true;
        }

        if (!newFile.exists()) {
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(newFile);

        for (String key : config.getKeys(false)) {
            if (!key.equalsIgnoreCase("inventory")
                    && config.isConfigurationSection(key)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void migrate() {
        if (oldFile.exists() && !newFile.exists()) {
            plugin.getNoticeManager().sendMigrationNotice(
                    oldFile.getName(),
                    newFile.getName()
            );

            try {
                Files.move(
                        oldFile.toPath(),
                        newFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );

                plugin.getNoticeManager().sendSuccessMigrationNotice(
                        oldFile.getName()
                );

            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorMigrationNotice(
                        "rename " + oldFile.getName() + " -> " + newFile.getName()
                );
                plugin.getLogger().severe(
                        "Failed to rename inventory_storage.yml to inventory_data.yml!"
                );
                e.printStackTrace();
                return;
            }
        }

        if (!newFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(newFile);
        ConfigurationSection inventory =
                config.getConfigurationSection("inventory");

        boolean migrated = false;

        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase("inventory")
                    || !config.isConfigurationSection(key)) {
                continue;
            }

            ConfigurationSection oldPlayerData =
                    config.getConfigurationSection(key);

            if (oldPlayerData == null) {
                continue;
            }

            if (inventory == null) {
                inventory = config.createSection("inventory");
            }

            if (inventory.contains(key)) {
                plugin.getLogger().warning(
                        "Inventory migration skipped UUID " + key
                                + " because inventory." + key + " already exists."
                );
                continue;
            }

            plugin.getNoticeManager().sendMigrationNotice(
                    "inventory_data.yml (Root keys)",
                    "inventory_data.yml (inventory.<UUID>)"
            );

            inventory.set(key, oldPlayerData);
            config.set(key, null);
            migrated = true;
        }

        if (migrated && MigrationUtils.saveConfig(plugin, config, newFile)) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "inventory_data.yml"
            );
        }
    }
}