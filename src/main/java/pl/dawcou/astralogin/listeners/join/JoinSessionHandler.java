package pl.dawcou.astralogin.listeners.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;

import java.time.Duration;
import java.util.UUID;

public class JoinSessionHandler {

    private final AstraLogin plugin;

    public JoinSessionHandler(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public boolean handleSession(Player p, UUID uuid, IPTrustManager.TrustLevel level, LoginSystem loginSystem) {
        boolean passwordBypassedBySession = false;

        // --- LOGIKA SESJI ---
        if (plugin.getConfig().getBoolean("features.session.enabled") && plugin.getPasswordManager().isRegistered(uuid)
                && !plugin.getPlayerDataManager().getBoolean(uuid, "account.suspicious", false)
                && level != IPTrustManager.TrustLevel.FATAL
                && level != IPTrustManager.TrustLevel.BAD) {
            if (p.getAddress() != null) {
                String currentIP = p.getAddress().getAddress().getHostAddress();
                String mainSavedIP = plugin.getIPManager().getIP(uuid.toString());

                if (mainSavedIP == null) {
                    plugin.getSessionManager().deleteSession(uuid);
                }
                else if (plugin.getSessionManager().hasActiveSession(uuid, currentIP)) {

                    // POBIERAMY STATUSY 2FA DLA TEGO KONKRETNEGO GRACZA
                    boolean is2FAEnabled = plugin.getTwoFactorManager().has2FA(uuid);

                    if (is2FAEnabled) {
                        // Gracz MA włączone 2FA na koncie -> sprawdzamy sesję drugiego stopnia
                        boolean is2FASessionEnabled = plugin.getConfig().getBoolean("security.2fa.session.enabled", true);
                        boolean hasActive2FA = plugin.getSessionManager().getTwoFactorSessionManager().hasActive2FASession(uuid);

                        if (!is2FASessionEnabled || !hasActive2FA) {
                            // [Kombinacja: Zwykła sesja jest, ale brakuje kodu 2FA / sesja 2FA wyłączona]
                            passwordBypassedBySession = true;
                            loginSystem.addWaitingFor2FA(uuid, uuid.toString());

                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));
                            Title title2fa = Title.title(
                                    Component.text(plugin.getLanguageManager().getMessage("title.twofactor")),
                                    Component.text(plugin.getLanguageManager().getMessage("twofactor.required")),
                                    Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                            );
                            plugin.getAdventure().player(p).showTitle(title2fa);
                        } else {
                            // [Kombinacja: Pełna sesja - Oba aktywne]
                            loginSystem.finishSession(p);
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.restored"));
                            plugin.getLogManager().log("Player " + p.getName() + " had an active session (Password + 2FA)");
                            return true; // Gracz zalogowany przez pełną sesję
                        }
                    } else {
                        // GRACZ NIE MA WŁĄCZONEGO 2FA -> skoro zwykła sesja jest, logujemy go od razu!
                        loginSystem.finishSession(p);
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.restored"));
                        plugin.getLogManager().log("Player " + p.getName() + " had an active session (Password only)");
                        return true; // Gracz zalogowany przez pełną sesję
                    }
                }
            }
        }

        return passwordBypassedBySession;
    }
}