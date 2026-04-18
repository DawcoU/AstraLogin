package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

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

        String[] savedParts = savedIP.split("\\.");
        String[] currentParts = currentIP.split("\\.");

        if (savedParts.length < 2 || currentParts.length < 2) return false;

        return savedParts[0].equals(currentParts[0]) && savedParts[1].equals(currentParts[1]);
    }

    private String c(FileConfiguration config, String path) {
        String s = config.getString(path);
        return s != null ? s.replace("&", "§") : "§cMissing config: " + path;
    }
}