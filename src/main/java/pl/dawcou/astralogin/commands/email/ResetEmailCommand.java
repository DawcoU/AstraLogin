package pl.dawcou.astralogin.commands.email;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.twofactor.method.EmailTwoFactorManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.Objects;
import java.util.UUID;

public final class ResetEmailCommand implements CommandExecutor {

    private final AstraLogin plugin;
    private final EmailTwoFactorManager emailManager;

    public ResetEmailCommand(AstraLogin plugin, EmailTwoFactorManager emailManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.emailManager = Objects.requireNonNull(emailManager, "emailManager");
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.resetmail")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-email.usage"));
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

        if (!emailManager.hasEmailAddress(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-email.no-email"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        emailManager.deleteEmailAddress(targetUUID);

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-email.admin.success")
                .replace("%player%", targetName));

        plugin.getLogManager().log("Admin " + sender.getName() + " reset email for player " + targetName);
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);

        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(
                    plugin.getLanguageManager().getWithPrefix("reset-email.player.message")
            );
        }

        return true;
    }
}