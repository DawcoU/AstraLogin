package pl.dawcou.astralogin.listeners.quit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.manage.InventoryManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnManager;
import pl.dawcou.astralogin.auth.password.PasswordManager;
import pl.dawcou.astralogin.auth.security.captcha.mode.chat.ChatCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.gui.GuiCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.map.MapCaptchaHandler;
import pl.dawcou.astralogin.auth.security.premium.PremiumManager;
import pl.dawcou.astralogin.auth.sessions.SessionManager;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.util.Objects;
import java.util.UUID;

public class QuitHandler {

    private final AstraLogin plugin;

    public QuitHandler(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void handleQuit(Player p, boolean isShutdown) {
        // Pobieramy aktualne instancje bezpośrednio z pluginu
        UUID uuid = p.getUniqueId();
        LoginSystem loginSystem = plugin.getLoginSystem();
        SpawnManager spawnManager = plugin.getSpawnManager();
        SessionManager sessionManager = plugin.getSessionManager();
        InventoryManager inventoryManager = plugin.getInventoryManager();
        TwoFactorManager twoFactorManager = plugin.getTwoFactorManager();
        PasswordManager passwordManager = plugin.getPasswordManager();
        PremiumManager premiumManager = plugin.getPremiumManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();

        // Czyszczenie sesji CAPTCHA
        GuiCaptchaHandler.removeSession(p);
        MapCaptchaHandler.removeSession(p, plugin);
        ChatCaptchaHandler.removeSession(p);
        plugin.getCaptchaManager().removeFlagged(uuid);

        if (loginSystem != null) {

            if (loginSystem.isLoggedIn(uuid)) {

                // --- GRACZ BYŁ ZALOGOWANY ---
                if (spawnManager != null) {
                    spawnManager.saveLastLocation(p);
                }

                if (plugin.getConfig().getBoolean("features.session.enabled") && sessionManager != null) {
                    sessionManager.saveSession(uuid, Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress());
                }

            } else {
                // --- GRACZ NIE BYŁ ZALOGOWANY ---
                if (inventoryManager != null) {
                    inventoryManager.restore(p);
                }
            }

            // Usuwamy z mapy zalogowanych
            loginSystem.removeLoggedIn(uuid);
            loginSystem.getTwoFactorSystem().removeWaitingFor2FA(uuid);
        }

        // Logika widoczności wykonywana tylko gdy serwer NIE jest wyłączany
        if (!isShutdown && plugin.isEnabled()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getSchedulerManager().runForEntity(online, () -> online.showPlayer(plugin, p));
            }
        }

        // Czyszczenie pamięci
        if (twoFactorManager != null) {
            twoFactorManager.invalidateSetup(uuid);
        }
        if (premiumManager != null) {
            premiumManager.removeAuthenticatedPlayer(uuid);;
        }
        if (passwordManager != null) {
            passwordManager.getPasswordHasher().cleanupPlayer(uuid);
        }

        if (!isShutdown) {
            if (playerDataManager != null) {
                playerDataManager.unloadPlayer(uuid);
            }
        }
    }
}