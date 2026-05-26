package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IPSecurity implements CommandExecutor {

    private final AstraLogin plugin;
    private final IPManager ipManager;
    public static int ipCheckOctets = 4;

    public IPSecurity(AstraLogin plugin, IPManager ipManager) {
        this.plugin = plugin;
        this.ipManager = ipManager;
    }

    // --- KOMENDA: /zresetujip ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Sprawdzamy permisję (bezpieczne dla gracza i konsoli)
        if (!sender.hasPermission("astralogin.resetip")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
            return true;
        }

        // Sprawdzamy argumenty
        if (args.length != 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-reset-ip"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String uuid = target.getUniqueId().toString();

        // Sprawdzamy czy IP w ogóle istnieje
        if (ipManager.getIP(uuid) == null) {
            // Tutaj używamy tej wbudowanej, bo to błąd systemowy
            plugin.getNoticeManager().sendNoIPSaved(sender);
            return true;
        }

        // Usuwamy IP
        ipManager.usunIP(uuid);

        // Pobieramy obiekt zalogowanego gracza, jeśli jest na serwerze
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            // Wyrzucamy gracza z serwera z wiadomością z managera językowego
            onlineTarget.kickPlayer(plugin.getLanguageManager().getMessage("player-reset-ip-kick"));
        }

        // Pobieramy wiadomość z messages.yml i podmieniamy %player%
        String successMsg = plugin.getLanguageManager().getWithPrefix("admin-reset-ip-success")
                .replace("%player%", args[0]);

        sender.sendMessage(successMsg);

        String adminName = sender.getName();
        // Pobieramy nick gracza, któremu resetujemy hasło
        String targetName = target.getName() != null ? target.getName() : args[0];
        plugin.getLogManager().log("Admin " + adminName + " reset IP for player " + targetName);
        return true;
    }

    // --- LOGIKA: Sprawdzanie bezpieczeństwa z uwzględnieniem konfiguracji ---
    public static boolean CheckIP(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equals(currentIP)) return true;

        if (savedIP.contains(".") && currentIP.contains(".")) {
            String[] s = savedIP.split("\\.");
            String[] c = currentIP.split("\\.");

            if (s.length < 4 || c.length < 4) {
                return false;
            }

            // Korzystamy bezpośrednio z pola w tej klasie, zabezpieczając zakres
            int octets = ipCheckOctets;
            if (octets < 1) octets = 1;
            if (octets > 4) octets = 4;

            for (int i = 0; i < octets; i++) {
                if (!s[i].equals(c[i])) {
                    return false;
                }
            }
            return true;
        }

        return savedIP.equalsIgnoreCase(currentIP);
    }
}