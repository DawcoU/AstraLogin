package pl.dawcou.astralogin.commands.account;

import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountsListCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public AccountsListCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.accountslist")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list.generating"));

        plugin.getSchedulerManager().runAsync(() -> {
            JsonObject namesSection = plugin.getGlobalDataManager().getJsonObject("usermap.names");

            List<String> formattedAccounts = new ArrayList<>();

            if (namesSection != null) {
                String format = plugin.getLanguageManager().getMessage("accounts-list.format");
                PlayerDataManager pdm = plugin.getPlayerDataManager();

                for (String lowerName : namesSection.keySet()) {
                    String realName = namesSection.get(lowerName).getAsString();
                    UUID uuid = plugin.getAccountManager().getUuidByUsername(lowerName);

                    if (uuid != null && plugin.getPasswordManager().isRegistered(uuid)) {
                        String knownName = pdm.getString(uuid, "account.name");

                        if (knownName == null) {
                            knownName = realName;
                        }

                        String line = format
                                .replace("%name%", knownName)
                                .replace("%uuid%", uuid.toString());

                        formattedAccounts.add(line);
                    }
                }
            }

            boolean empty = formattedAccounts.isEmpty();

            plugin.getSchedulerManager().runSync(() -> {
                if (empty) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("accounts-list.empty"));

                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);

                    return;
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list.header"));

                for (String accountLine : formattedAccounts) {
                    sender.sendMessage(accountLine);
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("accounts-list.footer"));

                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
            });
        });

        return true;
    }
}