package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class IPSecurity implements CommandExecutor {

    private final AstraLogin plugin;
    private final IPManager ipManager;

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

        // Pobieramy wiadomość z messages.yml i podmieniamy %player%
        String successMsg = plugin.getLanguageManager().getWithPrefix("admin-ip-reset-success")
                .replace("%player%", args[0]);

        sender.sendMessage(successMsg);
        return true;
    }

    // --- LOGIKA: Sprawdzanie bezpieczeństwa ---
    public static boolean isIPSafe(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equals(currentIP)) return true;

        // Sprawdzamy czy to IPv4 (ma kropki)
        if (savedIP.contains(".") && currentIP.contains(".")) {
            String[] s = savedIP.split("\\.");
            String[] c = currentIP.split("\\.");
            return s[0].equals(c[0]) && s[1].equals(c[1]); // Twoja logika 2 oktetów
        }

        // Jeśli to IPv6 (ma dwukropki), lepiej nie ryzykować "wycinania" części adresu
        // bo struktura IPv6 jest inna. Tu najlepiej sprawdzać całość.
        return savedIP.equalsIgnoreCase(currentIP);
    }
}