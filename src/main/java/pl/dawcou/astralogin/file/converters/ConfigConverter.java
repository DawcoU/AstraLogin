package pl.dawcou.astralogin.file.converters;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;

/*
 * Handles migrations of keys and sections within the plugin configuration file (config.yml).
 */
public class ConfigConverter {

    private final AstraLogin plugin;

    public ConfigConverter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    /*
     * Executes all configuration section migrations.
     */
    public void runAllMigrations() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean migrated = false;

        migrated |= migrateTimer(config);
        migrated |= migrateFeaturesToSecurity(config);
        migrated |= migrateHashingSettings(config);

        if (migrated) {
            saveConfig(config, configFile);
        }
    }

    /*
     * Migrates legacy timer configuration options and bossbar sub-keys.
     */
    private boolean migrateTimer(FileConfiguration config) {
        ConfigurationSection timer = config.getConfigurationSection("features.timer");

        if (timer == null) {
            timer = config.getConfigurationSection("timer");
        }

        if (timer == null) {
            return false;
        }

        boolean migrated = false;

        // login-time-enabled -> enabled
        if (timer.contains("login-time-enabled")) {
            plugin.getNoticeManager().sendMigrationNotice("features.timer.login-time-enabled", "features.timer.enabled");
            timer.set("enabled", timer.get("login-time-enabled"));
            timer.set("login-time-enabled", null);
            migrated = true;
        }

        // login-time-limit -> time-limit
        if (timer.contains("login-time-limit")) {
            plugin.getNoticeManager().sendMigrationNotice("features.timer.login-time-limit", "features.timer.time-limit");
            timer.set("time-limit", timer.get("login-time-limit"));
            timer.set("login-time-limit", null);
            migrated = true;
        }

        // Legacy bossbar configuration settings
        if (timer.contains("use-bossbar")
                || timer.contains("bossbar-color")
                || timer.contains("bossbar-style")) {

            plugin.getNoticeManager().sendMigrationNotice("timer.bossbar-*", "timer.boss-bar.*");

            ConfigurationSection bossBar = timer.getConfigurationSection("boss-bar");

            if (bossBar == null) {
                bossBar = timer.createSection("boss-bar");
            }

            if (timer.contains("use-bossbar")) {
                bossBar.set("use", timer.get("use-bossbar"));
                timer.set("use-bossbar", null);
            }

            if (timer.contains("bossbar-color")) {
                bossBar.set("color", timer.get("bossbar-color"));
                timer.set("bossbar-color", null);
            }

            if (timer.contains("bossbar-style")) {
                bossBar.set("style", timer.get("bossbar-style"));
                timer.set("bossbar-style", null);
            }

            migrated = true;
        }

        if (migrated) {
            plugin.getNoticeManager().sendSuccessMigrationNotice("config.yml (timer section)");
        }

        return migrated;
    }

    /*
     * Migrates sections from legacy 'features' root to 'security' root.
     */
    private boolean migrateFeaturesToSecurity(FileConfiguration config) {
        ConfigurationSection features = config.getConfigurationSection("features");
        if (features == null) {
            return false;
        }

        ConfigurationSection security = config.getConfigurationSection("security");
        if (security == null) {
            security = config.createSection("security");
        }

        boolean migrated = false;

        // Sections to move from 'features' to 'security'
        String[] sectionsToMigrate = {"attempts", "2fa", "pin", "auto-login"};

        for (String sectionName : sectionsToMigrate) {
            if (features.contains(sectionName) && !security.contains(sectionName)) {
                plugin.getNoticeManager().sendMigrationNotice("features." + sectionName, "security." + sectionName);
                security.set(sectionName, features.get(sectionName));
                features.set(sectionName, null);
                migrated = true;
            }
        }

        if (migrated) {
            plugin.getNoticeManager().sendSuccessMigrationNotice("config.yml (features -> security)");
        }

        return migrated;
    }

    /*
     * Migrates password hashing options to the new 'security.hashing' structure.
     */
    private boolean migrateHashingSettings(FileConfiguration config) {
        ConfigurationSection security = config.getConfigurationSection("security");
        if (security == null) {
            return false;
        }

        // Skip migration if the new 'hashing' section already exists
        if (security.contains("hashing")) {
            return false;
        }

        plugin.getNoticeManager().sendMigrationNotice("security.bcrypt", "security.hashing");

        ConfigurationSection hashing = security.createSection("hashing");
        hashing.set("algorithm", "ARGON2ID");

        // If old security.bcrypt existed, copy its values
        if (security.contains("bcrypt")) {
            ConfigurationSection oldBcrypt = security.getConfigurationSection("bcrypt");
            if (oldBcrypt != null) {
                hashing.set("bcrypt.cost", oldBcrypt.getInt("cost", 12));
            } else if (security.contains("bcrypt.cost")) {
                hashing.set("bcrypt.cost", security.getInt("bcrypt.cost", 12));
            }
            security.set("bcrypt", null);
        } else {
            hashing.set("bcrypt.cost", 12);
        }

        // Set default values for Argon2id
        hashing.set("argon2.iterations", 3);
        hashing.set("argon2.memory-kb", 32768);
        hashing.set("argon2.parallelism", 1);

        plugin.getNoticeManager().sendSuccessMigrationNotice("config.yml (hashing section)");
        return true;
    }

    /*
     * Thread-safe saving of configuration files.
     */
    private void saveConfig(FileConfiguration config, File file) {
        synchronized (config) {
            try {
                config.save(file);
            } catch (Exception e) {
                plugin.getNoticeManager().sendErrorMigrationNotice("save config.yml");
                plugin.getLogger().severe("Failed to save migrated config: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    /*
     * Checks if config.yml needs any section migrations.
     */
    public boolean needsMigration() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Check timer
        ConfigurationSection timer = config.getConfigurationSection("features.timer");
        if (timer == null) {
            timer = config.getConfigurationSection("timer");
        }
        if (timer != null && (timer.contains("login-time-enabled") || timer.contains("login-time-limit")
                || timer.contains("use-bossbar") || timer.contains("bossbar-color") || timer.contains("bossbar-style"))) {
            return true;
        }

        // Check features -> security
        ConfigurationSection features = config.getConfigurationSection("features");
        if (features != null) {
            String[] sections = {"attempts", "2fa", "pin", "auto-login"};
            for (String sec : sections) {
                if (features.contains(sec)) {
                    return true;
                }
            }
        }

        // Check hashing
        ConfigurationSection security = config.getConfigurationSection("security");
        return security != null && !security.contains("hashing");
    }
}