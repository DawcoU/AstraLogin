package pl.dawcou.astralogin.system.utils;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;

// ---------------------------------------------------------------- //
// SoundManager - Handles reading and playing configured sound effects
// ---------------------------------------------------------------- //
public class SoundManager {

    private final AstraLogin plugin;

    //-------------------------------------------------------------------------
    // Sound types enum for plugin events
    //-------------------------------------------------------------------------
    public enum SoundType {
        LOGIN("sounds.login"),
        REGISTER("sounds.register"),
        AUTO_LOGIN("sounds.auto-login"),
        WRONG_PASSWORD("sounds.wrong-password"),
        INVALID_PASSWORD_FORMAT("sounds.invalid-password-format"),
        SUCCESS("sounds.success"),
        FAIL("sounds.fail"),
        RATE_LIMITED("sounds.rate-limited"),
        SECURITY_REMINDER("sounds.security-reminder");

        private final String configPath;

        SoundType(String configPath) {
            this.configPath = configPath;
        }

        public String getConfigPath() {
            return configPath;
        }
    }

    public SoundManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- //
    // Plays sound for specified player using credentials from config.yml
    // ---------------------------------------------------------------- //
    public void playSound(Player player, SoundType soundType) {
        if (player == null || !player.isOnline()) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String path = soundType.getConfigPath();

        boolean enabled = config.getBoolean(path + ".enabled", true);
        if (!enabled) {
            return;
        }

        String soundName = config.getString(path + ".sound", "");
        if (soundName.isEmpty()) {
            return;
        }

        double rawVolume = config.getDouble(path + ".volume", 1.0);
        double rawPitch = config.getDouble(path + ".pitch", 1.0);

        // Safe bounds enforcement
        float volume = (float) Math.max(0.0, Math.min(2.0, rawVolume));
        float pitch = (float) Math.max(0.5, Math.min(2.0, rawPitch));

        plugin.getSchedulerManager().runForEntity(player, () -> {
            try {
                Sound bukkitSound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), bukkitSound, volume, pitch);
            } catch (IllegalArgumentException e) {
                // Log a warning about the invalid sound name from config
                plugin.getLogger().warning("Sound '" + soundName + "' defined at path '" + path
                        + "' is not a valid Bukkit sound! Attempting custom sound key playback...");

                // Fallback for custom resource pack sound keys
                player.playSound(player.getLocation(), soundName.toLowerCase(), volume, pitch);
            }
        });
    }
}