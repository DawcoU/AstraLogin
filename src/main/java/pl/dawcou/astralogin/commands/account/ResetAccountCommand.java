package pl.dawcou.astralogin.commands.account;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.UUID;

public class ResetAccountCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public ResetAccountCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.resetaccount")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("purge-account.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String inputName = args[0];
        String targetName = plugin.getAccountManager().getRegisteredName(inputName);
        UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

        if (targetUUID == null || targetName == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found")
                    .replace("%target%", inputName));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (p != null) {
            boolean confirmed = (args.length > 1 && args[1].equalsIgnoreCase("confirm"));

            if (!confirmed) {
                String baseMsgStr = plugin.getLanguageManager().getWithPrefix("purge-account.confirm");
                String btnTextStr = plugin.getLanguageManager().getMessage("purge-account.button");
                String hoverTextStr = plugin.getLanguageManager().getMessage("purge-account.hover");

                Component baseMsg = LegacyComponentSerializer.legacySection()
                        .deserialize(baseMsgStr + " ");

                Component confirmBtn = LegacyComponentSerializer.legacySection()
                        .deserialize(btnTextStr.replace("&", "§"))
                        .clickEvent(ClickEvent.runCommand("/resetaccount " + targetName + " confirm"))
                        .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr.replace("&", "§")));

                plugin.getAdventure().sender(sender).sendMessage(baseMsg.append(confirmBtn));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                return true;
            }
        }

        Player targetP = Bukkit.getPlayer(targetUUID);
        if (targetP != null && targetP.isOnline()) {
            String purgeReason = plugin.getLanguageManager().getMessage("purge-account.player.kick");
            targetP.kickPlayer(purgeReason);
        }

        plugin.getPlayerDataManager().deletePlayerData(targetUUID);

        plugin.getAttemptManager().clearAttempts(targetUUID);
        plugin.getAttemptManager().clearCaptchaAttempts(targetUUID);
        plugin.getTwoFactorManager().invalidateSetup(targetUUID);

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("purge-account.admin.success")
                .replace("%player%", targetName));

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);

        String adminName = sender.getName();
        plugin.getLogManager().log("Admin " + adminName + " PURGED all account data for player " + targetName);

        return true;
    }
}