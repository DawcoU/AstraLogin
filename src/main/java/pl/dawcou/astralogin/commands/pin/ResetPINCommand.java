package pl.dawcou.astralogin.commands.pin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.password.PINManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.UUID;

public class ResetPINCommand implements CommandExecutor {

    private final AstraLogin plugin;
    private final PINManager pinManager;

    public ResetPINCommand(AstraLogin plugin, PINManager pinManager) {
        this.plugin = plugin;
        this.pinManager = pinManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.resetpin")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String inputName = args[0];
        UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

        if (targetUUID == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found")
                    .replace("%target%", inputName));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String targetName = plugin.getAccountManager().getRegisteredName(inputName);
        if (targetName == null) {
            targetName = inputName;
        }

        if (!pinManager.hasPIN(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.no-has-pin"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        pinManager.deletePIN(targetUUID);

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.admin.success")
                .replace("%player%", targetName));
        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);

        String adminName = sender.getName();
        plugin.getLogManager().log("Admin " + adminName + " reset PIN for player " + targetName);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);

        if (targetPlayer != null) {
            targetPlayer.sendMessage(
                    plugin.getLanguageManager().getWithPrefix("reset-pin.player.message")
            );
        }

        return true;
    }
}