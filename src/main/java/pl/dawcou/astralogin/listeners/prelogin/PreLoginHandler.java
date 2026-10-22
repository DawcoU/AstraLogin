package pl.dawcou.astralogin.listeners.prelogin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.UUID;

public class PreLoginHandler {

    private final AstraLogin plugin;

    public PreLoginHandler(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void handlePreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        IPManager ipManager = plugin.getIPManager();

        String currentIP = e.getAddress().getHostAddress();
        String savedIP = ipManager.getIP(uuid);

        String playerName = e.getName();

        // Wykrywanie lokalnego adresu IP
        if (currentIP.equalsIgnoreCase("127.0.0.1") || currentIP.equalsIgnoreCase("::1")) {
            plugin.getLogger().warning("Detected local IP address (" + currentIP + ") for player " + playerName + "!");
            plugin.getLogger().warning("Your server is likely running behind a Proxy (BungeeCord/Velocity) without proper IP forwarding enabled.");
            plugin.getLogger().warning("Do NOT use IP-based security features, as all players will share the same local IP!");
        }

        // OCHRONA PRZED SPAMEM WEJŚĆ (Tylko jeśli nie ma jeszcze bana)
        if (plugin.getConfig().getBoolean("security.anti-spam.enabled", true)) {
            ipManager.addIPAttempt(currentIP);
        }

        // Sprawdzamy, czy ktoś o tym nicku (bez względu na wielkość liter) gra już teraz na serwerze
        boolean isAlreadyOnline = Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));

        if (isAlreadyOnline) {
            String kickReason = plugin.getLanguageManager().getMessage("login.already-online");

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickReason);
            plugin.getLogManager().log("Someone tried to join the account of an active player (" + playerName + ") with IP (" + currentIP + ")");
            plugin.getIpTrustManager().addTrustScore(
                    currentIP,
                    plugin.getIpTrustManager().getActiveAccountAttemptPoints()
            );
            return;
        }

        String registeredName = plugin.getAccountManager().getRegisteredNameIgnoreCase(playerName);

        // Jeśli znaleziono nick w pliku kont
        if (registeredName != null) {

            // Porównanie wielkości liter
            if (!registeredName.equals(playerName)) {
                String kickReason = plugin.getLanguageManager().getMessage("login.wrong-casing")
                        .replace("%registered%", registeredName);

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickReason);
                plugin.getLogManager().log("Player " + playerName + " tried to join with wrong casing. Registered name: " + registeredName);
                return;
            }
        }

        // JEDYNE SPRAWDZENIE BANA
        if (ipManager.isIPBanned(currentIP)) {
            long totalSeconds = ipManager.getIPBanTimeLeft(currentIP);

            // Teraz używamy klasy narzędziowej:
            String timeFormatted = TimeUtils.formatTime(totalSeconds);
            String reason = ipManager.getBanReason(currentIP);
            if (reason == null) reason = "UNKNOWN";

            String msg;
            if ("SPAM".equals(reason)) {
                msg = plugin.getLanguageManager().getMessage("security.ip-spam")
                        .replace("%time%", timeFormatted);
            } else {
                // Domyślnie traktujemy to jako ban za hasła (PASSWORD)
                msg = plugin.getLanguageManager().getMessage("security.max-attempts-ban")
                        .replace("%time%", timeFormatted);
            }

            // Ktoś z banem na IP próbuje wbić
            plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") tried to connect but is IP banned. Reason: " + reason);

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (msg));
            return;
        }

        // Sprawdzenie Zmiany IP
        if (plugin.getConfig().getBoolean("security.ip-security.enabled", true) && savedIP != null && !plugin.getIPManager().checkIP(uuid, savedIP, currentIP)) {
            plugin.getLogManager().log("Player " + playerName + " was detected with an IP mismatch. Current IP: " + currentIP + ", Saved IP: " + savedIP);
            plugin.getIpTrustManager().addTrustScore(
                    currentIP,
                    plugin.getIpTrustManager().getUnknownIpLoginPoints()
            );

            plugin.getPlayerDataManager().set(uuid, "account.suspicious", true);

            // Wysyłanie alertu do adminów
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("astralogin.alerts") && plugin.getConfig().getBoolean("visuals.suspicious-activity-alerts")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("security.suspicious-ip-alert")
                            .replace("%target%", playerName)
                            .replace("%ip%", currentIP));
                }
            }
            return;
        }

        // Sprawdzenie Multikonta
        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true) && savedIP == null) {
            int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
            int accountCount = ipManager.getNumberOfAccountsByIP(currentIP);

            if (accountCount >= limit) {
                // Przekroczenie limitu kont na jednym IP
                plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") was blocked by Anti-MultiAccount. Limit: " + limit + ", Current: " + accountCount);
                plugin.getIpTrustManager().addTrustScore(
                        currentIP,
                        plugin.getIpTrustManager().getMultiIpPoints()
                );

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (plugin.getLanguageManager().getMessage("security.multiaccount")));
                return;
            }
        }
    }
}