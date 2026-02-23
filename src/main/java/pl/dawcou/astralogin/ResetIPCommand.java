package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class ResetIPCommand implements CommandExecutor {

    private final AstraLogin plugin;
    private final IPManager ipData; // Używamy istniejącego managera

    // Zmieniamy konstruktor, żeby przyjmował gotowy ipData
    public ResetIPCommand(AstraLogin plugin, IPManager ipData) {
        this.plugin = plugin;
        this.ipData = ipData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();

        if (!sender.hasPermission("astralogin.resetip")) {
            // Dodaj PREFIX, żeby wyglądało spójnie!
            sender.sendMessage(AstraLogin.PREFIX + " " + c(config, "messages.no-permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(AstraLogin.PREFIX + " " + c(config, "messages.usage-reset-ip"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String uuid = target.getUniqueId().toString();

        // Sprawdźmy najpierw, czy ten gracz w ogóle ma zapisane IP
        if (ipData.getIP(uuid) == null) {
            sender.sendMessage(AstraLogin.PREFIX + " §cTen gracz nie ma zapisanego adresu IP!");
            return true;
        }

        ipData.usunIP(uuid);

        String successMsg = c(config, "messages.admin-ip-reset-success")
                .replace("%player%", args[0]);

        sender.sendMessage(AstraLogin.PREFIX + " " + successMsg);
        return true;
    }

    // Mały pomocnik do kolorów, żeby kod był czystszy
    private String c(FileConfiguration config, String path) {
        String s = config.getString(path);
        return s != null ? s.replace("&", "§") : "§cMissing config: " + path;
    }
}