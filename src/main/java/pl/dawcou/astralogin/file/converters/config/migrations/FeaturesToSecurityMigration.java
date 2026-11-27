package pl.dawcou.astralogin.file.converters.config.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigrationStep;

public class FeaturesToSecurityMigration implements ConfigMigrationStep {

    private static final String[] SECTIONS = {
            "attempts",
            "2fa",
            "pin",
            "auto-login"
    };

    private final AstraLogin plugin;

    public FeaturesToSecurityMigration(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean needsMigration(FileConfiguration config) {
        ConfigurationSection features =
                config.getConfigurationSection("features");

        if (features == null) {
            return false;
        }

        ConfigurationSection security =
                config.getConfigurationSection("security");

        for (String section : SECTIONS) {
            if (features.contains(section)
                    && (security == null || !security.contains(section))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean migrate(FileConfiguration config) {
        ConfigurationSection features =
                config.getConfigurationSection("features");

        if (features == null) {
            return false;
        }

        ConfigurationSection security =
                config.getConfigurationSection("security");

        if (security == null) {
            security = config.createSection("security");
        }

        boolean migrated = false;

        for (String sectionName : SECTIONS) {
            if (!features.contains(sectionName)) {
                continue;
            }

            if (security.contains(sectionName)) {
                plugin.getLogger().warning(
                        "Config migration conflict: security."
                                + sectionName
                                + " already exists. features."
                                + sectionName
                                + " was preserved."
                );
                continue;
            }

            plugin.getNoticeManager().sendMigrationNotice(
                    "features." + sectionName,
                    "security." + sectionName
            );

            security.set(sectionName, features.get(sectionName));
            features.set(sectionName, null);

            migrated = true;
        }

        if (migrated) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "config.yml (features -> security)"
            );
        }

        return migrated;
    }
}