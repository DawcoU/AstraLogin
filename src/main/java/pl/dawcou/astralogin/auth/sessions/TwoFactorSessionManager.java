package pl.dawcou.astralogin.auth.sessions;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.UUID;

public class TwoFactorSessionManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;

    public TwoFactorSessionManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public void saveSession2FA(UUID uuid, String ip) {
        if (uuid == null || ip == null) return;

        long now = System.currentTimeMillis();
        playerDataManager.set(uuid, "security.sessions.two-factor.created-at", now);
        playerDataManager.set(uuid, "security.sessions.two-factor.ip", ip);
    }

    public boolean hasActive2FASession(UUID uuid, String currentIP) {
        if (uuid == null || currentIP == null) return false;

        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        long timestamp = playerDataManager.getLong(uuid, "security.sessions.two-factor.created-at", 0L);
        String savedIP = playerDataManager.getString(uuid, "security.sessions.two-factor.ip");

        if (timestamp > 0 && (now - timestamp <= limit) && currentIP.equals(savedIP)) {
            return true;
        }

        deleteSession2FA(uuid);
        return false;
    }

    public void deleteSession2FA(UUID uuid) {
        if (uuid == null) return;

        playerDataManager.remove(uuid, "security.sessions.two-factor.created-at");
        playerDataManager.remove(uuid, "security.sessions.two-factor.ip");
    }

    public long get2FALimitMillis() {
        String timeStr = plugin.getConfig().getString("security.2fa.session.session-time", "2 days");


        return TimeUtils.parseTime(timeStr, 172800000L);
    }

    public int loadAllSessions() {
        int count = 0;
        long now = System.currentTimeMillis();
        long limit = get2FALimitMillis();

        for (UUID uuid : playerDataManager.getAllPlayerUUIDs()) {
            long timestamp = playerDataManager.getLong(uuid, "security.sessions.two-factor.created-at", 0L);
            String savedIP = playerDataManager.getString(uuid, "security.sessions.two-factor.ip");

            if (timestamp > 0 && savedIP != null && (now - timestamp <= limit)) {
                count++;
            } else if (timestamp > 0) {
                deleteSession2FA(uuid);
            }
        }
        return count;
    }
}