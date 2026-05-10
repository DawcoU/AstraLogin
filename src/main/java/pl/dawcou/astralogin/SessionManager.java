package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class SessionManager {

    private final AstraLogin plugin;
    private final File sessionFile;
    private FileConfiguration sessionConfig;

    public SessionManager(AstraLogin plugin) {
        this.plugin = plugin;
        // Tworzymy ścieżkę do folderu playerdata
        File dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.sessionFile = new File(dataDir, "session_data.yml");
        this.sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
    }

    // --- ZAPISYWANIE SESJI DO PLIKU ---
    public void saveSessionsToConfig() {
        sessionConfig.set("sessions", null);
        long now = System.currentTimeMillis();
        long limit = parseSessionTime(plugin.getConfig().getString("features.session-time", "15 minutes"));

        plugin.getLoginSystem().getSesje().forEach((uuid, timestamp) -> {
            // ZAPISUJEMY TYLKO SESJE, KTÓRE NIE WYGASŁY!
            if (now - timestamp < limit) {
                String path = "sessions." + uuid.toString();
                sessionConfig.set(path + ".timestamp", timestamp);
                String ip = plugin.getLoginSystem().getSesjeIP().get(uuid);
                sessionConfig.set(path + ".ip", ip);
            }
        });

        try {
            sessionConfig.save(sessionFile);
        } catch (IOException e) {
            plugin.getNoticeManager().sendSessionSaveError(e);
        }
    }

    // --- WCZYTYWANIE SESJI Z PLIKU ---
    public void loadSessionsFromConfig() {
        if (!sessionFile.exists()) return;

        sessionConfig = YamlConfiguration.loadConfiguration(sessionFile);
        if (!sessionConfig.contains("sessions")) return;

        // 1. Pobieramy limit czasu sesji z głównego configu AstraLogin
        long sessionLimit = parseSessionTime(plugin.getConfig().getString("features.session-time", "5 minutes"));
        long now = System.currentTimeMillis();
        int count = 0;

        for (String uuidStr : sessionConfig.getConfigurationSection("sessions").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                long timestamp = sessionConfig.getLong("sessions." + uuidStr + ".timestamp");
                String ip = sessionConfig.getString("sessions." + uuidStr + ".ip");

                // 2. KLUCZOWY WARUNEK: Sprawdzamy czy sesja jest jeszcze ważna
                if (now - timestamp < sessionLimit) {
                    // Sesja jest świeża -> wczytujemy do RAMu
                    plugin.getLoginSystem().getSesje().put(uuid, timestamp);
                    plugin.getLoginSystem().getSesjeIP().put(uuid, ip);
                    count++;
                }
            } catch (IllegalArgumentException e) {
                // Uszkodzony UUID w pliku
            }
        }

        // Teraz komunikat pokaże tylko te faktycznie aktywne sesje!
        if (count > 0) {
            plugin.getNoticeManager().sendSessionsLoaded(count);
        }
    }

    public static long parseSessionTime(String timeRaw) {
        try {
            String[] parts = timeRaw.split(" ");
            long value = Long.parseLong(parts[0]);
            if (parts.length > 1 && parts[1].startsWith("hour")) return value * 3600000;
            if (parts.length > 1 && parts[1].startsWith("second")) return value * 1000;
            return value * 60000;
        } catch (Exception ex) {
            return 300000;
        }
    }
}