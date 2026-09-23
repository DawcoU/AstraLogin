package pl.dawcou.astralogin.auth.sessions;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Menedżer sesji dwuetapowej weryfikacji (2FA) w plikach gracza
//--------------------------------------------------
public class TwoFactorSessionManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;

    private final Map<UUID, Long> twoFactorSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> twoFactorSessionsIP = new ConcurrentHashMap<>();

    public TwoFactorSessionManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    /**
     * Zapisuje aktywną sesję 2FA w pamięci RAM i w pliku JSON gracza.
     */
    public void saveSession2FA(UUID uuid, String ip) {
        if (uuid == null) return;

        long now = System.currentTimeMillis();

        twoFactorSessions.put(uuid, now);
        if (ip != null) {
            twoFactorSessionsIP.put(uuid, ip);
        }

        playerDataManager.set(uuid, "sessions.2fa.timestamp", now);
        if (ip != null) {
            playerDataManager.set(uuid, "sessions.2fa.ip", ip);
        }
    }

    /**
     * Sprawdza ważność sesji 2FA gracza.
     */
    public boolean hasActive2FASession(UUID uuid) {
        if (uuid == null) return false;

        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        // 1. Sprawdzamy najpierw RAM
        if (twoFactorSessions.containsKey(uuid)) {
            long timestamp = twoFactorSessions.get(uuid);
            if (now - timestamp >= limit) {
                deleteSession2FA(uuid);
                return false;
            }
            return true;
        }

        // 2. Wczytujemy dane z pliku gracza w razie braku w RAM
        long timestamp = playerDataManager.getLong(uuid, "sessions.2fa.timestamp", 0L);
        String savedIP = playerDataManager.getString(uuid, "sessions.2fa.ip");

        if (timestamp > 0 && (now - timestamp < limit)) {
            twoFactorSessions.put(uuid, timestamp);
            if (savedIP != null) {
                twoFactorSessionsIP.put(uuid, savedIP);
            }
            return true;
        }

        deleteSession2FA(uuid);
        return false;
    }

    /**
     * Usuwa sesję 2FA z pamięci RAM i z pliku gracza.
     */
    public void deleteSession2FA(UUID uuid) {
        if (uuid == null) return;

        twoFactorSessions.remove(uuid);
        twoFactorSessionsIP.remove(uuid);

        playerDataManager.remove(uuid, "sessions.2fa");
    }

    public long get2FALimitMillis() {
        String timeStr = plugin.getConfig().getString("security.2fa.session.session-time", "2 days");
        return TimeUtils.parseTime(timeStr, 172800000L);
    }

    /**
     * Wczytuje i weryfikuje aktywne sesje 2FA z plików graczy.
     */
    public int loadSessionsFromFiles() {
        int count = 0;
        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        for (UUID uuid : playerDataManager.getAllPlayerUUIDs()) {
            long timestamp = playerDataManager.getLong(uuid, "sessions.2fa.timestamp", 0L);
            String savedIP = playerDataManager.getString(uuid, "sessions.2fa.ip");

            if (timestamp > 0 && (now - timestamp < limit)) {
                twoFactorSessions.put(uuid, timestamp);
                if (savedIP != null) {
                    twoFactorSessionsIP.put(uuid, savedIP);
                }
                count++;
            } else if (timestamp > 0) {
                deleteSession2FA(uuid);
            }
        }
        return count;
    }

    public void reload() {
        twoFactorSessions.clear();
        twoFactorSessionsIP.clear();
    }
}