package pl.dawcou.astralogin.file.converters;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;

public class ConfigConverter {

    private final AstraLogin plugin;

    public ConfigConverter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void runAllMigrations() {
        migrateTimer();
    }

    private void migrateTimer() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection timer = config.getConfigurationSection("timer");

        if (timer == null) {
            return;
        }

        // Jeżeli nowy format już istnieje, nic nie rób.
        if (timer.contains("enabled")) {
            return;
        }

        boolean migrated = false;

        // login-time-enabled -> enabled
        if (timer.contains("login-time-enabled")) {
            timer.set("enabled", timer.get("login-time-enabled"));
            timer.set("login-time-enabled", null);
            migrated = true;
        }

        // login-time-limit -> time-limit
        if (timer.contains("login-time-limit")) {
            timer.set("time-limit", timer.get("login-time-limit"));
            timer.set("login-time-limit", null);
            migrated = true;
        }

        // Stare ustawienia bossbara
        if (timer.contains("use-bossbar")
                || timer.contains("bossbar-color")
                || timer.contains("bossbar-style")) {

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
            saveConfig(config, configFile);
        }
    }

    private void saveConfig(FileConfiguration config, File file) {
        synchronized (config) {
            try {
                config.save(file);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save migrated config: " + file.getName());
                e.printStackTrace();
            }
        }
    }
}