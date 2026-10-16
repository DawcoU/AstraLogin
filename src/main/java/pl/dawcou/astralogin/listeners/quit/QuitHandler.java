package pl.dawcou.astralogin.listeners.quit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.manage.InventoryManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnManager;
import pl.dawcou.astralogin.auth.sessions.SessionManager;
import pl.dawcou.astralogin.auth.security.TwoFactorManager;

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

        if (loginSystem != null && loginSystem.getLoggedIn() != null) {

            if (loginSystem.getLoggedIn().contains(uuid)) {

                // --- GRACZ BYŁ ZALOGOWANY ---
                if (spawnManager != null) {
                    spawnManager.saveLastLocation(p);
                }

                if (plugin.getConfig().getBoolean("features.session.enabled") && sessionManager != null) {
                    sessionManager.saveSession(uuid, p.getAddress().getAddress().getHostAddress());
                }

            } else {
                // --- GRACZ NIE BYŁ ZALOGOWANY ---
                if (inventoryManager != null) {
                    inventoryManager.restore(p);
                }
            }
        }

        // Logika widoczności wykonywana tylko gdy serwer NIE jest wyłączany
        if (!isShutdown && plugin.isEnabled()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getSchedulerManager().runForEntity(online, () -> online.showPlayer(plugin, p));
            }
        }

        // Usuwamy z mapy zalogowanych
        if (loginSystem != null && loginSystem.getLoggedIn() != null) {
            loginSystem.getLoggedIn().remove(uuid);
        }

        // CZYSZCZENIE (zamrożonych setup'ów)
        if (twoFactorManager != null) {
            twoFactorManager.invalidateSetup(uuid);
        }

        // Czyszczenie pamięci
        plugin.getPremiumManager().removeAuthenticatedPlayer(uuid);
        plugin.getPremiumManager().clearPendingBypass(uuid);

        plugin.getPasswordManager().getPasswordHasher().cleanupPlayer(uuid);

        if (!isShutdown) {
            plugin.getPlayerDataManager().unloadPlayer(uuid);
        }
    }
}