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

public class SessionManager {

    private final AstraLogin plugin;
    private final TwoFactorSessionManager twoFactorSessionManager;

    private final File sessionFile;
    private FileConfiguration sessionConfig;

    // --- MAPY RAM DLA SESJI HASŁA ---
    private final Map<UUID, Long> sessions = new HashMap<>();
    private final Map<UUID, String> sessionsIP = new HashMap<>();

    public TwoFactorSessionManager getTwoFactorSessionManager() { return twoFactorSessionManager; }

    public SessionManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.twoFactorSessionManager = new TwoFactorSessionManager(plugin);

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        sessionFile = new File(dataDir, "session_data.yml");
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    // At 3:00 AM I opened the AstraLogin JAR with Windows XP Notepad,
    // removed several random Chinese symbols from the binary, and saved it.
    // The JVM immediately exploded and released binary radiation,
    // scattering broken zeros and ones across the filesystem.
    // I have learned nothing from this experience.

    // ==========================================
    //          LOGIKA OBSŁUGI SESJI
    // ==========================================

    public void saveSessionsToConfig() {
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        sessions.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", timestamp);
                sessionConfig.set(path + ".ip", sessionsIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", null);
                sessionConfig.set(path + ".ip", null);
            }
        });

        save();
    }

    public void loadSessionsFromConfig() {
        if (!sessionFile.exists()) return;

        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection section = sessionConfig.getConfigurationSection("sessions");
        if (section == null) return;

        long sessionLimit = getSessionLimitMillis();
        long now = System.currentTimeMillis();
        int count = 0;

        sessions.clear();
        sessionsIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".ip");

                if (timestamp > 0 && (now - timestamp < sessionLimit)) {
                    sessions.put(uuid, timestamp);
                    sessionsIP.put(uuid, ip);
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (count > 0) {
            plugin.getNoticeManager().sendSessionsLoaded(count);
        }
    }

    public long getSessionLimitMillis() {
        String timeStr = plugin.getConfig().getString("features.session.session-time", "15 minutes");
        return TimeUtils.parseTime(timeStr, 900000L);
    }

    public void deleteSession(UUID uuid) {
        if (uuid == null) return;

        sessions.remove(uuid);
        sessionsIP.remove(uuid);

        String path = "sessions." + uuid;
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path + ".timestamp", null);
            sessionConfig.set(path + ".ip", null);

            // Jeśli po usunięciu sesji głównej cała sekcja UUID jest pusta, usuń ją całkowicie
            var section = sessionConfig.getConfigurationSection(path);
            if (section == null || section.getKeys(false).isEmpty()) {
                sessionConfig.set(path, null);
            }

            save();
        }
    }

    public boolean hasActiveSession(UUID uuid, String currentIP) {
        if (uuid == null || currentIP == null) return false;
        if (!sessions.containsKey(uuid) || !sessionsIP.containsKey(uuid)) return false;

        String savedIP = sessionsIP.get(uuid);
        if (!currentIP.equals(savedIP)) {
            deleteSession(uuid);
            return false;
        }

        long lastLogout = sessions.get(uuid);
        long now = System.currentTimeMillis();

        if (now - lastLogout <= getSessionLimitMillis()) {
            return true;
        } else {
            deleteSession(uuid);
            return false;
        }
    }

    public void saveSession(UUID uuid, String ip) {
        if (uuid == null || ip == null) return;

        long now = System.currentTimeMillis();
        String path = "sessions." + uuid;

        sessions.put(uuid, now);
        sessionsIP.put(uuid, ip);

        sessionConfig.set(path + ".timestamp", now);
        sessionConfig.set(path + ".ip", ip);

        save();
    }

    public void reload() {
        // Wczytujemy plik z dysku na nowo do obiektu konfiguracyjnego
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);

        loadSessionsFromConfig();
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