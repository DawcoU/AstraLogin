package pl.dawcou.astralogin.file.converters.config;

import org.bukkit.configuration.file.FileConfiguration;

public interface ConfigMigrationStep {

    boolean needsMigration(FileConfiguration config);

    boolean migrate(FileConfiguration config);
}