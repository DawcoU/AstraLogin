package pl.dawcou.astralogin.auth.sessions;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.UUID;

public class SessionManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final TwoFactorSessionManager twoFactorSessionManager;

    public SessionManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.twoFactorSessionManager = new TwoFactorSessionManager(plugin, playerDataManager);
    }

    public TwoFactorSessionManager getTwoFactorSessionManager() {
        return twoFactorSessionManager;
    }

    public void saveSession(UUID uuid, String ip) {
        if (uuid == null || ip == null) return;

        long now = System.currentTimeMillis();
        playerDataManager.set(uuid, "security.sessions.login.created-at", now);
        playerDataManager.set(uuid, "security.sessions.login.ip", ip);
    }

    public boolean hasActiveSession(UUID uuid, String currentIP) {
        if (uuid == null || currentIP == null) return false;

        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        long timestamp = playerDataManager.getLong(uuid, "security.sessions.login.created-at", 0L);
        String savedIP = playerDataManager.getString(uuid, "security.sessions.login.ip");

        if (timestamp > 0 && (now - timestamp <= limit) && currentIP.equals(savedIP)) {
            return true;
        }

        deleteSession(uuid);
        return false;
    }

    public void deleteSession(UUID uuid) {
        if (uuid == null) return;

        playerDataManager.remove(uuid, "security.sessions.login.created-at");
        playerDataManager.remove(uuid, "security.sessions.login.ip");
    }

    public long getSessionLimitMillis() {
        String timeStr = plugin.getConfig().getString("features.session.session-time", "15 minutes");
        return TimeUtils.parseTime(timeStr, 900000L);
    }

    public void loadAllSessions() {
        int loadedStandardSessions = 0;
        long now = System.currentTimeMillis();
        long limit = getSessionLimitMillis();

        for (UUID uuid : playerDataManager.getAllPlayerUUIDs()) {
            long timestamp = playerDataManager.getLong(uuid, "security.sessions.login.created-at", 0L);
            String savedIP = playerDataManager.getString(uuid, "security.sessions.login.ip");

            if (timestamp > 0 && savedIP != null && (now - timestamp <= limit)) {
                loadedStandardSessions++;
            } else if (timestamp > 0) {
                deleteSession(uuid);
            }
        }

        int loaded2FASessions = twoFactorSessionManager.loadAllSessions();
        int totalLoaded = loadedStandardSessions + loaded2FASessions;

        if (totalLoaded > 0) {
            plugin.getNoticeManager().sendSessionsLoaded(totalLoaded);
        }
    }

    public void reload() {
        loadAllSessions();
        twoFactorSessionManager.loadAllSessions();
    }
}