package pl.dawcou.astralogin.commands.password;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.Objects;
import java.util.UUID;

public final class ResetPassCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public ResetPassCommand(AstraLogin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.resetpassword")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.usage"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        String inputName = args[0];
        UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

        if (targetUUID == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found")
                    .replace("%target%", inputName));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        String targetName = plugin.getAccountManager().getRegisteredName(inputName);
        if (targetName == null) {
            targetName = inputName;
        }

        if (!plugin.getPasswordManager().isRegistered(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.no-account"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        plugin.getPasswordManager().deletePassword(targetUUID);

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.admin.success")
                .replace("%player%", targetName));

        String adminName = sender.getName();
        plugin.getLogManager().log("Admin " + adminName + " reset password for player " + targetName);
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);

        if (targetPlayer != null && targetPlayer.isOnline()) {
            String playerIP = Objects.requireNonNull(targetPlayer.getAddress())
                    .getAddress().getHostAddress();

            plugin.getIPManager().resetIPAttempts(playerIP);
            targetPlayer.kickPlayer(plugin.getLanguageManager().getMessage("reset-password.player.kick"));
        }

        return true;
    }
}
