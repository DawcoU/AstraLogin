package pl.dawcou.astralogin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final AstraLogin plugin;
    private final File sessionFile;
    private FileConfiguration sessionConfig;

    // Jedyne, uniwersalne mapy sesji w całym pluginie
    private final Map<UUID, Long> sesje = new HashMap<>();
    private final Map<UUID, String> sesjeIP = new HashMap<>();

    public Map<UUID, Long> getSesje() {
        return this.sesje;
    }

    public Map<UUID, String> getSesjeIP() {
        return this.sesjeIP;
    }

    public SessionManager(AstraLogin plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.sessionFile = new File(dataDir, "session_data.yml");
        this.sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    public void saveSessionsToConfig() {
        sessionConfig.set("sessions", null);
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        this.sesje.forEach((uuid, timestamp) -> {
            if (now - timestamp < limit) {
                String path = "sessions." + uuid;
                sessionConfig.set(path + ".timestamp", timestamp);
                sessionConfig.set(path + ".ip", this.sesjeIP.get(uuid));
            }
        });

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            plugin.getNoticeManager().sendSessionSaveError(e);
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

        // Czyszczenie starych danych w pamięci na wypadek reloadu pluginu
        this.sesje.clear();
        this.sesjeIP.clear();

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".ip");

                if (now - timestamp < sessionLimit) {
                    this.sesje.put(uuid, timestamp);
                    this.sesjeIP.put(uuid, ip);
                    count++;
                }
            } catch (IllegalArgumentException e) {
                // Ignorowanie uszkodzonych rekordów UUID
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

    /**
     * Główna metoda usuwająca sesję na podstawie obiektu UUID.
     * Idealna do użycia w finishLogin i eventach w grze!
     */
    public void deleteSession(UUID uuid) {
        if (uuid == null) {
            return;
        }

        // 1. Czyszczenie z lokalnej pamięci RAM
        this.sesje.remove(uuid);
        this.sesjeIP.remove(uuid);

        // 2. Czyszczenie z pliku YML
        String path = "sessions." + uuid.toString();
        if (sessionConfig.contains(path)) {
            sessionConfig.set(path, null);

            try {
                sessionConfig.save(sessionFile);
            } catch (IOException e) {
                plugin.getNoticeManager().sendSessionSaveError(e);
            }
        }
    }

    public void DeleteSession(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return;
        }

        try {
            UUID uuidObiekt = UUID.fromString(uuidString);
            deleteSession(uuidObiekt); // Wywołujemy główną logikę powyżej
        } catch (IllegalArgumentException e) {
            plugin.getNoticeManager().sendInvalidUUIDError(e);
        }
    }
}