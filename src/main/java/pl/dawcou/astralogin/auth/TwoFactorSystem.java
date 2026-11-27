package pl.dawcou.astralogin.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerFailedTwoFactorEvent;
import pl.dawcou.astralogin.api.events.PlayerTwoFactorSuccessEvent;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorMethod;
import pl.dawcou.astralogin.auth.security.twofactor.method.EmailTwoFactorManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TwoFactorSystem {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;

    private final Map<UUID, String> waitingFor2FA = new ConcurrentHashMap<>();

    public TwoFactorSystem(AstraLogin plugin, LoginSystem loginSystem) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
    }

    public boolean isWaitingFor2FA(UUID uuid) {
        return waitingFor2FA.containsKey(uuid);
    }

    public void addWaitingFor2FA(UUID uuid, String uuidString) {
        waitingFor2FA.put(uuid, uuidString);
    }

    public void removeWaitingFor2FA(UUID uuid) {
        waitingFor2FA.remove(uuid);
    }

    // ----------------------------------------------------------------
    // TWO-FACTOR AUTHENTICATION
    // ----------------------------------------------------------------

    public void requestTwoFactor(Player p, boolean playReminderSound) {
        UUID uuid = p.getUniqueId();

        addWaitingFor2FA(uuid, uuid.toString());

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));

        Title title = Title.title(
                Component.text(plugin.getLanguageManager().getMessage("title.twofactor")),
                Component.text(plugin.getLanguageManager().getMessage("twofactor.required")),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofHours(1),
                        Duration.ofMillis(500)
                )
        );

        plugin.getAdventure().player(p).showTitle(title);

        if (playReminderSound) {
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.SECURITY_REMINDER);
        }

        sendMailLoginCodeIfNeeded(p, uuid);
    }

    private void sendMailLoginCodeIfNeeded(Player p, UUID uuid) {
        TwoFactorManager twoFactorManager = plugin.getTwoFactorManager();
        if (twoFactorManager == null || twoFactorManager.getMethod(uuid) != TwoFactorMethod.MAIL) {
            return;
        }

        EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
        if (!emailManager.isConfigured() || emailManager.hasPendingCode(uuid)) {
            return;
        }

        String email = emailManager.getEmailAddress(uuid);
        if (!EmailSender.isValidEmail(email)) {
            return;
        }

        emailManager.sendVerificationCode(uuid, email, success -> {
            if (!p.isOnline()) {
                return;
            }

            if (success) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.login.code-sent").replace("%email%", email));
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.send-failed"));
            }
        });
    }

    public void completeTwoFactorLogin(Player p, String ip, boolean backupCode) {
        UUID uuid = p.getUniqueId();

        PlayerTwoFactorSuccessEvent event = new PlayerTwoFactorSuccessEvent(p, ip);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        removeWaitingFor2FA(uuid);
        loginSystem.finishLogin(p);

        if (plugin.getConfig().getBoolean("security.2fa.session.enabled")) {
            plugin.getSessionManager().getTwoFactorSessionManager()
                    .saveSession2FA(uuid, ip);
        }

        plugin.getLogManager().log(
                "Player " + p.getName() +
                        (backupCode
                                ? " entered a valid backup code and was logged in"
                                : " entered the correct 2FA code and was logged in")
        );

        plugin.getIpTrustManager().changeTrustScore(
                ip,
                plugin.getIpTrustManager().getTwofaSuccessPoints()
        );

        p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.success"));
        plugin.getSuspicionManager().decrementSuspicion(uuid);
        plugin.getSoundManager().playSound(p, SoundManager.SoundType.LOGIN);

        p.sendTitle(
                plugin.getLanguageManager().getMessage("title.login"),
                plugin.getLanguageManager().getMessage("title.login-subtitle"),
                10, 40, 10
        );
    }

    public void handleInvalidTwoFactorCode(Player player, String ip) {
        PlayerFailedTwoFactorEvent event = new PlayerFailedTwoFactorEvent(player, ip);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        plugin.getAttemptManager().addTrial(player, AttemptType.TWOFACTOR);

        plugin.getIpTrustManager().changeTrustScore(
                ip,
                plugin.getIpTrustManager().getTwofaFailedPoints()
        );

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.WRONG_PASSWORD);
    }
}