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
        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.sessionFile = new File(dataDir, "session_data.yml");
        this.sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    // ==========================================
    //          LOGIKA OBSŁUGI SESJI
    // ==========================================

    public void saveSessionsToConfig() {
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        this.sesje.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", timestamp);
                sessionConfig.set(path + ".ip", this.sesjeIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", null);
                sessionConfig.set(path + ".ip", null);
            }
        });

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save2FAToConfig() {
        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        this.dfaSesje.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", timestamp);
                sessionConfig.set(path + ".2fa-ip", this.dfaIP.get(uuid));
            } else {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".2fa-timestamp", null);
                sessionConfig.set(path + ".2fa-ip", null);
            }
        });

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadSessionsFromConfig() {
        if (!sessionFile.exists()) return;

        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection section = sessionConfig.getConfigurationSection("sessions");
        if (section == null) return;

        long sessionLimit = getSessionLimitMillis();
        long now = System.currentTimeMillis();
        int count = 0;

        this.sesje.clear();
        this.sesjeIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".ip");

                if (timestamp > 0 && (now - timestamp < sessionLimit)) {
                    this.sesje.put(uuid, timestamp);
                    this.sesjeIP.put(uuid, ip);
                    count++;
                }
            } catch (IllegalArgumentException e) {
                // Ignorowanie uszkodzonych rekordow
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

        this.sesje.remove(uuid);
        this.sesjeIP.remove(uuid);

        String path = "sessions." + uuid;
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path + ".timestamp", null);
            sessionConfig.set(path + ".ip", null);

            // Jeśli po usunięciu sesji głównej cała sekcja UUID jest pusta, usuń ją całkowicie
            var section = sessionConfig.getConfigurationSection(path);
            if (section == null || section.getKeys(false).isEmpty()) {
                sessionConfig.set(path, null);
            }

            try {
                sessionConfig.save(sessionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean hasActiveSession(UUID uuid, String currentIP) {
        if (uuid == null || currentIP == null) return false;
        if (!this.sesje.containsKey(uuid) || !this.sesjeIP.containsKey(uuid)) return false;

        String savedIP = this.sesjeIP.get(uuid);
        if (!currentIP.equals(savedIP)) {
            deleteSession(uuid);
            return false;
        }

        long lastLogout = this.sesje.get(uuid);
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

        this.sesje.put(uuid, now);
        this.sesjeIP.put(uuid, ip);

        sessionConfig.set(path + ".timestamp", now);
        sessionConfig.set(path + ".ip", ip);

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveSession2FA(UUID uuid, String ip) {
        if (uuid == null) return;

        long now = System.currentTimeMillis();
        String path = "sessions." + uuid;

        this.dfaSesje.put(uuid, now);
        this.dfaIP.put(uuid, ip);

        sessionConfig.set(path + ".2fa-timestamp", now);
        sessionConfig.set(path + ".2fa-ip", ip);

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteSession2FA(UUID uuid) {
        if (uuid == null) return;

        this.dfaSesje.remove(uuid);
        this.dfaIP.remove(uuid);

        String path = "sessions." + uuid;
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path + ".2fa-timestamp", null);
            sessionConfig.set(path + ".2fa-ip", null);

            // Jeśli po usunięciu 2FA cała sekcja UUID jest pusta, usuń ją całkowicie
            var section = sessionConfig.getConfigurationSection(path);
            if (section == null || section.getKeys(false).isEmpty()) {
                sessionConfig.set(path, null);
            }

            try {
                sessionConfig.save(sessionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void load2FAFromConfig() {
        if (!sessionFile.exists()) return;

        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection section = sessionConfig.getConfigurationSection("sessions");
        if (section == null) return;

        long dfaLimit = get2FALimitMillis();
        long now = System.currentTimeMillis();

        this.dfaSesje.clear();
        this.dfaIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".2fa-timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".2fa-ip");

                if (timestamp > 0 && (now - timestamp < dfaLimit)) {
                    this.dfaSesje.put(uuid, timestamp);
                    this.dfaIP.put(uuid, ip);
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
        if (uuid == null || !this.dfaSesje.containsKey(uuid)) return false;

        long timestamp = this.dfaSesje.get(uuid);
        long now = System.currentTimeMillis();

        if (now - timestamp >= get2FALimitMillis()) {
            deleteSession2FA(uuid);
            return false;
        }
        return true;
    }

    public void reload() {
        // Wczytujemy plik z dysku na nowo do obiektu konfiguracyjnego
        this.sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);

        loadSessionsFromConfig();
        load2FAFromConfig();
    }
}