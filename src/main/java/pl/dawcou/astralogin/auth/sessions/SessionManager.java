package pl.dawcou.astralogin.auth.sessions;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Menedżer tymczasowych sesji graczy oparty na PlayerDataManager
//--------------------------------------------------
public class SessionManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final TwoFactorSessionManager twoFactorSessionManager;

    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionsIP = new ConcurrentHashMap<>();

    public SessionManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.twoFactorSessionManager = new TwoFactorSessionManager(plugin, playerDataManager);
    }

    public TwoFactorSessionManager getTwoFactorSessionManager() {
        return twoFactorSessionManager;
    }

    /**
     * Zapisuje nową sesję w pamięci RAM oraz w pliku JSON gracza.
     */
    public void saveSession(UUID uuid, String ip) {
        if (uuid == null || ip == null) return;

        long now = System.currentTimeMillis();

        sessions.put(uuid, now);
        sessionsIP.put(uuid, ip);

        playerDataManager.set(uuid, "sessions.timestamp", now);
        playerDataManager.set(uuid, "sessions.ip", ip);
    }

    /**
     * Sprawdza, czy gracz posiada aktywną sesję i czy IP się zgadza.
     */
    public boolean hasActiveSession(UUID uuid, String currentIP) {
        if (uuid == null || currentIP == null) return false;

        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        // 1. Sprawdzamy najpierw pamieć RAM
        if (sessions.containsKey(uuid) && sessionsIP.containsKey(uuid)) {
            String savedIP = sessionsIP.get(uuid);
            long lastLogout = sessions.get(uuid);

            if (!currentIP.equals(savedIP) || (now - lastLogout > limit)) {
                deleteSession(uuid);
                return false;
            }
            return true;
        }

        // 2. Jeśli brak w RAM (np. po restarcie serwera), wczytujemy z pliku gracza
        long timestamp = playerDataManager.getLong(uuid, "sessions.timestamp", 0L);
        String savedIP = playerDataManager.getString(uuid, "sessions.ip");

        if (timestamp > 0 && savedIP != null && (now - timestamp <= limit)) {
            if (currentIP.equals(savedIP)) {
                sessions.put(uuid, timestamp);
                sessionsIP.put(uuid, savedIP);
                return true;
            }
        }

        // Jeśli dane wygasły lub IP jest inne
        deleteSession(uuid);
        return false;
    }

    /**
     * Usuwa sesję z pamięci RAM i czyści odpowiednie pola w pliku gracza.
     */
    public void deleteSession(UUID uuid) {
        if (uuid == null) return;

        sessions.remove(uuid);
        sessionsIP.remove(uuid);

        playerDataManager.remove(uuid, "sessions.timestamp");
        playerDataManager.remove(uuid, "sessions.ip");
    }

    public long getSessionLimitMillis() {
        String timeStr = plugin.getConfig().getString("features.session.session-time", "15 minutes");
        return TimeUtils.parseTime(timeStr, 900000L);
    }

    /**
     * Wczytuje aktywne sesje zwykłe oraz 2FA ze wszystkich plików graczy i wysyła komunikat w konsoli.
     */
    public void loadAllSessionsFromFiles() {
        int loadedStandardSessions = 0;
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        for (UUID uuid : playerDataManager.getAllPlayerUUIDs()) {
            long timestamp = playerDataManager.getLong(uuid, "sessions.timestamp", 0L);
            String savedIP = playerDataManager.getString(uuid, "sessions.ip");

            if (timestamp > 0 && savedIP != null && (now - timestamp <= limit)) {
                sessions.put(uuid, timestamp);
                sessionsIP.put(uuid, savedIP);
                loadedStandardSessions++;
            } else if (timestamp > 0) {
                deleteSession(uuid);
            }
        }

        int loaded2FASessions = twoFactorSessionManager.loadSessionsFromFiles();
        int totalLoaded = loadedStandardSessions + loaded2FASessions;

        if (totalLoaded > 0) {
            plugin.getNoticeManager().sendSessionsLoaded(totalLoaded);
        }
    }

    public void reload() {
        sessions.clear();
        sessionsIP.clear();
        twoFactorSessionManager.reload();
        loadAllSessionsFromFiles();
    }
}