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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MoveAccountCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public MoveAccountCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.moveaccount")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String oldNickname = args[0];
        String newNickname = args[1];

        if (oldNickname.equalsIgnoreCase(newNickname)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.player-same"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        UUID oldUUIDObj = plugin.getAccountManager().getUuidByUsername(oldNickname);
        if (oldUUIDObj == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found")
                    .replace("%target%", oldNickname));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String registeredOldName = plugin.getAccountManager().getRegisteredName(oldNickname);
        String finalOldNickname = (registeredOldName != null) ? registeredOldName : oldNickname;

        Player oldPlayerP = Bukkit.getPlayer(oldUUIDObj);
        Player newPlayerP = Bukkit.getPlayerExact(newNickname);

        if ((oldPlayerP != null && oldPlayerP.isOnline()) || (newPlayerP != null && newPlayerP.isOnline())) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.players-online")
                    .replace("%old%", finalOldNickname)
                    .replace("%new%", newNickname));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        File playersFolder = new File(plugin.getDataFolder(), "data/players");
        File oldPlayerFile = new File(playersFolder, oldUUIDObj + ".json");

        if (!oldPlayerFile.exists()) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.old-player-no-data")
                    .replace("%target%", finalOldNickname));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        UUID newUUIDObj = plugin.getAccountManager().getUuidByUsername(newNickname);
        if (newUUIDObj == null) {
            newUUIDObj = UUID.nameUUIDFromBytes(("OfflinePlayer:" + newNickname).getBytes(StandardCharsets.UTF_8));
        }

        File newPlayerFile = new File(playersFolder, newUUIDObj + ".json");

        if (newPlayerFile.exists()) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.new-player-exists")
                    .replace("%uuid%", newUUIDObj.toString()));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (p != null) {
            boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

            if (!confirmed) {
                String baseMsgStr = plugin.getLanguageManager().getWithPrefix("account-move.confirm");
                String btnTextStr = plugin.getLanguageManager().getMessage("account-move.button");
                String hoverTextStr = plugin.getLanguageManager().getMessage("account-move.hover");

                Component baseMsg = LegacyComponentSerializer.legacySection()
                        .deserialize(baseMsgStr + " ");

                Component confirmBtn = LegacyComponentSerializer.legacySection()
                        .deserialize(btnTextStr)
                        .clickEvent(ClickEvent.runCommand("/moveaccount " + finalOldNickname + " " + newNickname + " confirm"))
                        .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                plugin.getAdventure().sender(sender).sendMessage(baseMsg.append(confirmBtn));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                return true;
            }
        }

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.start")
                .replace("%old%", finalOldNickname)
                .replace("%new%", newNickname));

        final UUID finalNewUUIDObj = newUUIDObj;
        final String newUUIDStr = newUUIDObj.toString();

        String adminName = sender.getName();

        plugin.getSchedulerManager().runAsync(() -> {
            try {
                plugin.getPlayerDataManager().unloadPlayer(oldUUIDObj);

                if (!oldPlayerFile.renameTo(newPlayerFile)) {
                    plugin.getSchedulerManager().runSync(() -> {
                        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.error-migration"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    });
                    return;
                }

                plugin.getPlayerDataManager().set(finalNewUUIDObj, "account.name", newNickname);

                plugin.getGlobalDataManager().remove("usermap.names." + finalOldNickname.toLowerCase());
                plugin.getGlobalDataManager().remove("usermap.uuids." + finalOldNickname.toLowerCase());

                plugin.getGlobalDataManager().set("usermap.names." + newNickname.toLowerCase(), newNickname);
                plugin.getGlobalDataManager().set("usermap.uuids." + newNickname.toLowerCase(), newUUIDStr);
                plugin.getGlobalDataManager().save();

                plugin.getSchedulerManager().runSync(plugin::reload);

                plugin.getSchedulerManager().runSync(() -> {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.success")
                            .replace("%old%", finalOldNickname)
                            .replace("%new%", newNickname));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                });

                plugin.getLogManager().log("Player " + finalOldNickname + " has been successfully migrated to " + newNickname + " by " + adminName);

            } catch (Exception e) {
                plugin.getSchedulerManager().runSync(() -> {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account-move.error-migration"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                });
                e.printStackTrace();
            }
        });

        return true;
    }
}