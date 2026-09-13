package pl.dawcou.astralogin.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.ip.IPManager;

//--------------------------------------------------
// IPResetCommand - komenda do resetowania IP gracza
//--------------------------------------------------
public class IPResetCommand implements CommandExecutor {

    private final AstraLogin plugin;
    private final IPManager ipManager;

    public IPResetCommand(AstraLogin plugin, IPManager ipManager) {
        this.plugin = plugin;
        this.ipManager = ipManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("zresetujip") || command.getName().equalsIgnoreCase("resetip")) {
            if (!sender.hasPermission("astralogin.resetip")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.usage"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            String uuid = target.getUniqueId().toString();

            if (ipManager.getIP(uuid) == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.no-ip"));
                return true;
            }

            ipManager.deleteIP(uuid);

            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                String kickReason = plugin.getLanguageManager().getMessage("reset-ip.player-kick");
                onlineTarget.kickPlayer(kickReason);
            }

            String successMsg = plugin.getLanguageManager().getWithPrefix("reset-ip.admin-success")
                    .replace("%player%", args[0]);

            sender.sendMessage(successMsg);

            String adminName = sender.getName();
            String targetName = target.getName() != null ? target.getName() : args[0];
            plugin.getLogManager().log("Admin " + adminName + " reset IP for player " + targetName);
            return true;
        }
        return false;
    }
}