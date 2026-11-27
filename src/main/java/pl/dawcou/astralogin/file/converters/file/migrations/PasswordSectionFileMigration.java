package pl.dawcou.astralogin.file.converters.file.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;

public class PasswordSectionFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File passFile;

    public PasswordSectionFileMigration(AstraLogin plugin) {
        this.plugin = plugin;
        this.passFile = new File(
                plugin.getDataFolder(),
                "player_data/passwords.yml"
        );
    }

    @Override
    public boolean needsMigration() {
        if (!passFile.exists()) {
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(passFile);

        if (config.isConfigurationSection("players")) {
            return true;
        }

        ConfigurationSection passwords =
                config.getConfigurationSection("passwords");

        if (passwords == null) {
            return false;
        }

        for (String uuid : passwords.getKeys(false)) {
            if (passwords.get(uuid) instanceof String) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void migrate() {
        if (!passFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(passFile);
        boolean migrated = false;

        if (config.isConfigurationSection("players")) {
            plugin.getNoticeManager().sendMigrationNotice(
                    "passwords.yml (players)",
                    "passwords.yml (passwords)"
            );

            ConfigurationSection oldSection =
                    config.getConfigurationSection("players");

            ConfigurationSection newSection =
                    config.getConfigurationSection("passwords");

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

        ConfigurationSection passwords =
                config.getConfigurationSection("passwords");

        if (passwords != null) {
            for (String uuid : passwords.getKeys(false)) {
                Object value = passwords.get(uuid);

                if (value instanceof String hash) {
                    if (!migrated) {
                        plugin.getNoticeManager().sendMigrationNotice(
                                "passwords.yml (String hash)",
                                "passwords.yml (Nested object)"
                        );
                    }

                    passwords.set(uuid, null);
                    passwords.set(uuid + ".password", hash);
                    migrated = true;
                }
            }
        }

        if (migrated && MigrationUtils.saveConfig(plugin, config, passFile)) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "passwords.yml"
            );
        }
    }
}