package pl.dawcou.astralogin.file.converters.config.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigrationStep;

public class UpdateSectionMigration implements ConfigMigrationStep {

    private final AstraLogin plugin;

    public UpdateSectionMigration(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean needsMigration(FileConfiguration config) {
        return config.contains("settings.check-updates");
    }

    @Override
    public boolean migrate(FileConfiguration config) {
        if (!config.contains("settings.check-updates")) {
            return false;
        }

        ConfigurationSection settings =
                config.getConfigurationSection("settings");

        if (settings == null) {
            return false;
        }

        ConfigurationSection update =
                settings.getConfigurationSection("update");

        if (update == null) {
            if (settings.contains("update")) {
                plugin.getLogger().warning(
                        "Config migration conflict: settings.update "
                                + "is not a section. Legacy setting was preserved."
                );
                return false;
            }

            update = settings.createSection("update");
        }

        if (update.contains("check-updates")) {
            plugin.getLogger().warning(
                    "Config migration conflict: "
                            + "settings.update.check-updates already exists. "
                            + "Legacy setting was preserved."
            );
            return false;
        }

        update.set(
                "check-updates",
                settings.get("check-updates")
        );

        settings.set("check-updates", null);

        plugin.getNoticeManager().sendMigrationNotice(
                "settings.check-updates",
                "settings.update.check-updates"
        );

        plugin.getNoticeManager().sendSuccessMigrationNotice(
                "config.yml (update section)"
        );

        return true;
    }
}