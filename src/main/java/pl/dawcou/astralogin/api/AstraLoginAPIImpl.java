package pl.dawcou.astralogin.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.suspicious.SuspicionLevel;

import java.util.UUID;

public class AstraLoginAPIImpl implements AstraLoginAPI {

    private final AstraLogin plugin;

    public AstraLoginAPIImpl(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------------------------------------------
    // STANY GRACZA
    // ----------------------------------------------------------------------------------------------------

    @Override
    public boolean isRegistered(@NonNull UUID uuid) {
        return plugin.getPasswordManager() != null && plugin.getPasswordManager().isRegistered(uuid);
    }

    @Override
    public boolean isLogged(@NonNull UUID uuid) {
        return plugin.getLoginSystem() != null && plugin.getLoginSystem().getLoggedIn().contains(uuid);
    }

    @Override
    public boolean hasTwoFactor(@NonNull UUID uuid) {
        return plugin.getTwoFactorManager() != null && plugin.getTwoFactorManager().has2FA(uuid);
    }

    @Override
    public boolean isPremium(@NonNull UUID uuid) {
        return plugin.getPremiumManager() != null && plugin.getPremiumManager().isAuthenticated(uuid);
    }

    @Override
    public @Nullable String getLastIp(@NonNull UUID uuid) {
        if (plugin.getIPManager() == null) {
            return null;
        }
        return plugin.getIPManager().getIP(uuid);
    }

    @Override
    public int getTrustScore(@NonNull String ip) {
        if (plugin.getIpTrustManager() == null) {
            return 0;
        }
        return plugin.getIpTrustManager().getTrustScore(ip);
    }

    @Override
    public @NonNull SuspicionLevel getSuspicionLevel(@NonNull UUID uuid) {
        if (plugin.getSuspicionManager() == null) {
            return SuspicionLevel.NONE;
        }
        return plugin.getSuspicionManager().getSuspicionLevel(uuid);
    }

    @Override
    public boolean isSuspicious(@NonNull UUID uuid) {
        if (plugin.getSuspicionManager() == null) {
            return false;
        }
        return plugin.getSuspicionManager().isSuspicious(uuid);
    }

    @Override
    public boolean hasActiveCaptcha(@NonNull Player p) {
        return plugin.getCaptchaManager().hasCaptcha(p);
    }

    @Override
    public boolean hasActiveSession(@NonNull UUID uuid, @NonNull String currentIp) {
        if (plugin.getSessionManager() == null) {
            return false;
        }
        return plugin.getSessionManager().hasActiveSession(uuid, currentIp);
    }

    @Override
    public boolean hasActive2FASession(@NonNull UUID uuid, @NonNull String currentIp) {
        if (plugin.getSessionManager() == null || plugin.getSessionManager().getTwoFactorSessionManager() == null) {
            return false;
        }
        return plugin.getSessionManager().getTwoFactorSessionManager().hasActive2FASession(uuid, currentIp);
    }

    // ----------------------------------------------------------------------------------------------------
    // AKCJE GRACZA
    // ----------------------------------------------------------------------------------------------------

    @Override
    public void forceLogin(@NonNull Player p) {
        if (plugin.getLoginSystem() != null) {
            plugin.getLoginSystem().finishLogin(p);
        }
    }

    @Override
    public void forceLogout(@NonNull Player p) {
        if (plugin.getLoginSystem() != null) {
            plugin.getLoginSystem().logout(p);
        }
    }

    @Override
    public void unregister(@NonNull UUID uuid) {
        if (plugin.getPasswordManager() != null) {
            plugin.getPasswordManager().deletePassword(uuid);
        }
    }

    @Override
    public void setTrustScore(@NonNull String ip, int score) {
        if (plugin.getIpTrustManager() != null) {
            plugin.getIpTrustManager().setTrustScore(ip, score);
        }
    }

    @Override
    public void setSuspicionLevel(@NonNull UUID uuid, @NonNull SuspicionLevel level) {
        if (plugin.getSuspicionManager() != null) {
            plugin.getSuspicionManager().setSuspicionLevel(uuid, level);
        }
    }
}