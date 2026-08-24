package pl.dawcou.astralogin.auth;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.system.LoginUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final AstraLogin plugin;
    private final File sessionFile;
    private FileConfiguration sessionConfig;

    // --- MAPY RAM DLA SESJI HASŁA ---
    private final Map<UUID, Long> sesje = new HashMap<>();
    private final Map<UUID, String> sesjeIP = new HashMap<>();

    // --- MAPY RAM DLA MODUŁU 2FA ---
    private final Map<UUID, Long> dfaSesje = new HashMap<>();
    private final Map<UUID, String> dfaIP = new HashMap<>();

    public SessionManager(AstraLogin plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        sessionFile = new File(dataDir, "session_data.yml");
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    // ==========================================
    //          LOGIKA OBSŁUGI SESJI
    // ==========================================

    public void saveSessionsToConfig() {
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        sesje.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", timestamp);
                sessionConfig.set(path + ".ip", sesjeIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", null);
                sessionConfig.set(path + ".ip", null);
            }
        });

        save();
    }

    public void save2FAToConfig() {
        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        dfaSesje.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", timestamp);
                sessionConfig.set(path + ".2fa-ip", dfaIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", null);
                sessionConfig.set(path + ".2fa-ip", null);
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

        sesje.clear();
        sesjeIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".ip");

                if (timestamp > 0 && (now - timestamp < sessionLimit)) {
                    sesje.put(uuid, timestamp);
                    sesjeIP.put(uuid, ip);
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
        return LoginUtils.parseTime(timeStr, 900000L);
    }

    public void deleteSession(UUID uuid) {
        if (uuid == null) return;

        sesje.remove(uuid);
        sesjeIP.remove(uuid);

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
        if (!sesje.containsKey(uuid) || !sesjeIP.containsKey(uuid)) return false;

        String savedIP = sesjeIP.get(uuid);
        if (!currentIP.equals(savedIP)) {
            deleteSession(uuid);
            return false;
        }

        long lastLogout = sesje.get(uuid);
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

        sesje.put(uuid, now);
        sesjeIP.put(uuid, ip);

        sessionConfig.set(path + ".timestamp", now);
        sessionConfig.set(path + ".ip", ip);

        save();
    }

    public void saveSession2FA(UUID uuid, String ip) {
        if (uuid == null) return;

        long now = System.currentTimeMillis();
        String path = "sessions." + uuid;

        dfaSesje.put(uuid, now);
        dfaIP.put(uuid, ip);

        sessionConfig.set(path + ".2fa-timestamp", now);
        sessionConfig.set(path + ".2fa-ip", ip);

        save();
    }

    public void deleteSession2FA(UUID uuid) {
        if (uuid == null) return;

        dfaSesje.remove(uuid);
        dfaIP.remove(uuid);

        String path = "sessions." + uuid;
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path + ".2fa-timestamp", null);
            sessionConfig.set(path + ".2fa-ip", null);

            // Jeśli po usunięciu 2FA cała sekcja UUID jest pusta, usuń ją całkowicie
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

        dfaSesje.clear();
        dfaIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".2fa-timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".2fa-ip");

                if (timestamp > 0 && (now - timestamp < dfaLimit)) {
                    dfaSesje.put(uuid, timestamp);
                    dfaIP.put(uuid, ip);
                }
            } catch (IllegalArgumentException e) {
                // Ignorowanie blednych struktur UUID
            }
        }
    }

    public long get2FALimitMillis() {
        String timeStr = plugin.getConfig().getString("features.2fa.session.session-time", "2 days");
        return LoginUtils.parseTime(timeStr, 172800000L);
    }

    public boolean hasActive2FASession(UUID uuid) {
        if (uuid == null || !dfaSesje.containsKey(uuid)) return false;

        long timestamp = dfaSesje.get(uuid);
        long now = System.currentTimeMillis();

        if (now - timestamp >= get2FALimitMillis()) {
            deleteSession2FA(uuid);
            return false;
        }
        return true;
    }

    public void reload() {
        // Wczytujemy plik z dysku na nowo do obiektu konfiguracyjnego
        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);

        loadSessionsFromConfig();
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