package pl.dawcou.astralogin.listeners.join;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerSessionLoginEvent;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;
import pl.dawcou.astralogin.auth.security.suspicious.SuspicionLevel;

import java.util.UUID;

public class JoinSessionHandler {

    private final AstraLogin plugin;

    public JoinSessionHandler(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public boolean handleSession(Player p, UUID uuid, IPTrustManager.TrustLevel level, LoginSystem loginSystem) {

        // Sprawdzamy, czy zwykłe sesje są w ogóle dozwolone.
        if (!plugin.getConfig().getBoolean("features.session.enabled")
                || !plugin.getPasswordManager().isRegistered(uuid)
                || plugin.getSuspicionManager().hasAtLeast(uuid, SuspicionLevel.HIGH)
                || level == IPTrustManager.TrustLevel.FATAL
                || level == IPTrustManager.TrustLevel.BAD) {
            return false;
        }

        if (p.getAddress() == null) {
            return false;
        }

        String currentIP = p.getAddress().getAddress().getHostAddress();
        String mainSavedIP = plugin.getIPManager().getIP(uuid);

        if (mainSavedIP == null) {
            plugin.getSessionManager().deleteSession(uuid);
            return false;
        }

        if (!plugin.getSessionManager().hasActiveSession(uuid, currentIP)) {
            return false;
        }

        boolean is2FAEnabled = plugin.getTwoFactorManager().has2FA(uuid);

        // Konto posiada 2FA — sprawdzamy również sesję drugiego stopnia.
        if (is2FAEnabled) {
            boolean is2FASessionEnabled = plugin.getConfig()
                    .getBoolean("security.2fa.session.enabled", true);

            boolean hasActive2FA = plugin.getSessionManager()
                    .getTwoFactorSessionManager()
                    .hasActive2FASession(uuid, currentIP);

            if (!is2FASessionEnabled || !hasActive2FA) {
                loginSystem.requestTwoFactor(p, false);
                return true;
            }
        }

        // Pełna sesja — hasło oraz ewentualnie 2FA.
        return restoreFullSession(p, currentIP, loginSystem, is2FAEnabled);
    }

    private boolean restoreFullSession(
            Player p,
            String ip,
            LoginSystem loginSystem,
            boolean is2FAEnabled
    ) {
        PlayerSessionLoginEvent event = new PlayerSessionLoginEvent(p, ip);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        loginSystem.finishSession(p);

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.restored"));

        plugin.getLogManager().log(
                "Player " + p.getName() + " had an active session ("
                        + (is2FAEnabled ? "Password + 2FA" : "Password only") + ")"
        );

        return true;
    }
}