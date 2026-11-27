package pl.dawcou.astralogin.file.converters.config.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigrationStep;

public class TimerMigration implements ConfigMigrationStep {

    private final AstraLogin plugin;

    public TimerMigration(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean needsMigration(FileConfiguration config) {
        ConfigurationSection timer = getTimerSection(config);

        return timer != null && (
                timer.contains("login-time-enabled")
                        || timer.contains("login-time-limit")
                        || timer.contains("use-bossbar")
                        || timer.contains("bossbar-color")
                        || timer.contains("bossbar-style")
        );
    }

    @Override
    public boolean migrate(FileConfiguration config) {
        ConfigurationSection timer = getTimerSection(config);

        if (timer == null) {
            return false;
        }

        boolean migrated = false;

        if (timer.contains("login-time-enabled")) {
            if (!timer.contains("enabled")) {
                plugin.getNoticeManager().sendMigrationNotice(
                        "timer.login-time-enabled",
                        "timer.enabled"
                );

                timer.set("enabled", timer.get("login-time-enabled"));
                timer.set("login-time-enabled", null);
                migrated = true;
            } else {
                logConflict("timer.enabled");
            }
        }

        if (timer.contains("login-time-limit")) {
            if (!timer.contains("time-limit")) {
                plugin.getNoticeManager().sendMigrationNotice(
                        "timer.login-time-limit",
                        "timer.time-limit"
                );

                timer.set("time-limit", timer.get("login-time-limit"));
                timer.set("login-time-limit", null);
                migrated = true;
            } else {
                logConflict("timer.time-limit");
            }
        }

        if (timer.contains("use-bossbar")
                || timer.contains("bossbar-color")
                || timer.contains("bossbar-style")) {

            ConfigurationSection bossBar =
                    timer.getConfigurationSection("boss-bar");

            if (bossBar == null) {
                if (timer.contains("boss-bar")) {
                    logConflict("timer.boss-bar");
                    return migrated;
                }

                bossBar = timer.createSection("boss-bar");
            }

            boolean bossBarMigrated = false;

            if (timer.contains("use-bossbar")) {
                if (!bossBar.contains("use")) {
                    bossBar.set("use", timer.get("use-bossbar"));
                    timer.set("use-bossbar", null);
                    bossBarMigrated = true;
                } else {
                    logConflict("timer.boss-bar.use");
                }
            }

            if (timer.contains("bossbar-color")) {
                if (!bossBar.contains("color")) {
                    bossBar.set("color", timer.get("bossbar-color"));
                    timer.set("bossbar-color", null);
                    bossBarMigrated = true;
                } else {
                    logConflict("timer.boss-bar.color");
                }
            }

            if (timer.contains("bossbar-style")) {
                if (!bossBar.contains("style")) {
                    bossBar.set("style", timer.get("bossbar-style"));
                    timer.set("bossbar-style", null);
                    bossBarMigrated = true;
                } else {
                    logConflict("timer.boss-bar.style");
                }
            }

            if (bossBarMigrated) {
                plugin.getNoticeManager().sendMigrationNotice(
                        "timer.bossbar-*",
                        "timer.boss-bar.*"
                );
                migrated = true;
            }
        }

        if (migrated) {
            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "config.yml (timer section)"
            );
        }

        return migrated;
    }

    private ConfigurationSection getTimerSection(FileConfiguration config) {
        ConfigurationSection timer =
                config.getConfigurationSection("features.timer");

        if (timer == null) {
            timer = config.getConfigurationSection("timer");
        }

        return timer;
    }

    private void logConflict(String path) {
        plugin.getLogger().warning(
                "Config migration conflict: destination already exists: " + path
                        + ". Legacy value was preserved."
        );
    }
}