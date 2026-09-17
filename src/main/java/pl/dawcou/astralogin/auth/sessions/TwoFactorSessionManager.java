package pl.dawcou.astralogin.auth.sessions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TwoFactorSessionManager {

    private final AstraLogin plugin;
    private final File sessionFile;
    private FileConfiguration sessionConfig;

    private final Map<UUID, Long> TwoFactorSessions = new HashMap<>();
    private final Map<UUID, String> TwoFactorSessionsIP = new HashMap<>();

    public TwoFactorSessionManager(AstraLogin plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        sessionFile = new File(dataDir, "session_data.yml");
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    public void save2FAToConfig() {
        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        TwoFactorSessions.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", timestamp);
                sessionConfig.set(path + ".2fa-ip", TwoFactorSessionsIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", null);
                sessionConfig.set(path + ".2fa-ip", null);
            }
        });

        save();
    }

    public void saveSession2FA(UUID uuid, String ip) {
        if (uuid == null) return;

        long now = System.currentTimeMillis();
        String path = "sessions." + uuid;

        TwoFactorSessions.put(uuid, now);
        TwoFactorSessionsIP.put(uuid, ip);

        sessionConfig.set(path + ".2fa-timestamp", now);
        sessionConfig.set(path + ".2fa-ip", ip);

        save();
    }

    public void deleteSession2FA(UUID uuid) {
        if (uuid == null) return;

        TwoFactorSessions.remove(uuid);
        TwoFactorSessionsIP.remove(uuid);

        String path = "sessions." + uuid;
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path + ".2fa-timestamp", null);
            sessionConfig.set(path + ".2fa-ip", null);

            var section = sessionConfig.getConfigurationSection(path);
            if (section == null || section.getKeys(false).isEmpty()) {
                sessionConfig.set(path, null);
            }

            save();
        }
    }

    public void load2FAFromConfig() {
        if (!sessionFile.exists()) return;

        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection section = sessionConfig.getConfigurationSection("sessions");
        if (section == null) return;

        long dfaLimit = get2FALimitMillis();
        long now = System.currentTimeMillis();

        TwoFactorSessions.clear();
        TwoFactorSessionsIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".2fa-timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".2fa-ip");

                if (timestamp > 0 && (now - timestamp < dfaLimit)) {
                    TwoFactorSessions.put(uuid, timestamp);
                    TwoFactorSessionsIP.put(uuid, ip);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public long get2FALimitMillis() {
        String timeStr = plugin.getConfig().getString("security.2fa.session.session-time", "2 days");
        return TimeUtils.parseTime(timeStr, 172800000L);
    }

    public boolean hasActive2FASession(UUID uuid) {
        if (uuid == null || !TwoFactorSessions.containsKey(uuid)) return false;

        long timestamp = TwoFactorSessions.get(uuid);
        long now = System.currentTimeMillis();

        if (now - timestamp >= get2FALimitMillis()) {
            deleteSession2FA(uuid);
            return false;
        }
        return true;
    }

    public void reload() {
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        load2FAFromConfig();
    }

    private void save() {
        synchronized (sessionConfig) {
            try {
                sessionConfig.save(sessionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}