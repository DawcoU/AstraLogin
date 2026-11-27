package pl.dawcou.astralogin.file.converters.config.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigrationStep;

public class HashingSettingsMigration implements ConfigMigrationStep {

    private final AstraLogin plugin;

    public HashingSettingsMigration(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean needsMigration(FileConfiguration config) {
        ConfigurationSection security =
                config.getConfigurationSection("security");

        return security != null && !security.contains("hashing");
    }

    @Override
    public boolean migrate(FileConfiguration config) {
        ConfigurationSection security =
                config.getConfigurationSection("security");

        if (security == null || security.contains("hashing")) {
            return false;
        }

        plugin.getNoticeManager().sendMigrationNotice(
                "security.bcrypt",
                "security.hashing"
        );

        ConfigurationSection hashing =
                security.createSection("hashing");

        hashing.set("algorithm", "ARGON2ID");

        if (security.contains("bcrypt")) {
            ConfigurationSection oldBcrypt =
                    security.getConfigurationSection("bcrypt");

            if (oldBcrypt != null) {
                hashing.set(
                        "bcrypt.cost",
                        oldBcrypt.getInt("cost", 12)
                );

            } else if (security.contains("bcrypt.cost")) {
                hashing.set(
                        "bcrypt.cost",
                        security.getInt("bcrypt.cost", 12)
                );
            }

            security.set("bcrypt", null);

        } else {
            hashing.set("bcrypt.cost", 12);
        }

        hashing.set("argon2.iterations", 3);
        hashing.set("argon2.memory-kb", 32768);
        hashing.set("argon2.parallelism", 1);

        plugin.getNoticeManager().sendSuccessMigrationNotice(
                "config.yml (hashing section)"
        );

        return true;
    }
}