package pl.dawcou.astralogin.commands.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.system.utils.SoundManager;

import java.util.UUID;

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
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujip") || command.getName().equalsIgnoreCase("resetip")) {
            if (!sender.hasPermission("astralogin.resetip")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.usage"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            String targetName = args[0];
            UUID targetUuid = plugin.getAccountManager().getUuidByUsername(targetName);

            if (targetUuid == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            String uuid = targetUuid.toString();

            if (ipManager.getIP(uuid) == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.no-ip"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            ipManager.deleteIP(uuid);

            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            if (onlineTarget != null) {
                String kickReason = plugin.getLanguageManager().getMessage("reset-ip.player-kick");
                onlineTarget.kickPlayer(kickReason);
            }

            String successMsg = plugin.getLanguageManager().getWithPrefix("reset-ip.admin-success")
                    .replace("%player%", targetName);

            sender.sendMessage(successMsg);
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
            }

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset IP for player " + targetName);
            return true;
        }
        return false;
    }
}