package pl.dawcou.astralogin.file.converters.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.migrations.*;
import pl.dawcou.astralogin.file.converters.file.FileMigration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ConfigMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File configFile;
    private final List<ConfigMigrationStep> migrations;

    public ConfigMigration(AstraLogin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        this.migrations = List.of(
                new TimerMigration(plugin),
                new FeaturesToSecurityMigration(plugin),
                new HashingSettingsMigration(plugin),
                new IpSecurityMigration(plugin),
                new UpdateSectionMigration(plugin)
        );
    }

    @Override
    public boolean needsMigration() {
        if (!configFile.exists()) {
            return false;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(configFile);

        for (ConfigMigrationStep migration : migrations) {
            if (migration.needsMigration(config)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void migrate() {
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(configFile);

        boolean migrated = false;

        for (ConfigMigrationStep migration : migrations) {
            try {
                if (migration.migrate(config)) {
                    migrated = true;
                }
            } catch (Exception e) {
                plugin.getLogger().severe(
                        "Failed to execute config migration: "
                                + migration.getClass().getSimpleName()
                );
                e.printStackTrace();
            }
        }

        if (!migrated) {
            return;
        }

        if (!saveConfig(config)) {
            plugin.getNoticeManager().sendErrorMigrationNotice(
                    "save config.yml"
            );

            plugin.getLogger().severe(
                    "Failed to save migrated config.yml. "
                            + "Original config file was preserved."
            );
        }
    }

    private boolean saveConfig(FileConfiguration config) {
        File tempFile = new File(
                configFile.getParentFile(),
                configFile.getName() + ".migration.tmp"
        );

        try {
            config.save(tempFile);

            try {
                Files.move(
                        tempFile.toPath(),
                        configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile.toPath(),
                        configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return true;

        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Could not save migrated config.yml."
            );
            e.printStackTrace();
            return false;

        } finally {
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning(
                        "Could not delete temporary config migration file: "
                                + tempFile.getName()
                );
            }
        }
    }
}