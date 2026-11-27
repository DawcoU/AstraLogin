package pl.dawcou.astralogin.commands.account;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.UUID;

public class AccountCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public AccountCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.account")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("account.usage"));
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

        String targetUUIDString = targetUUID.toString();
        PlayerDataManager pdm = plugin.getPlayerDataManager();

        String ip = plugin.getIPManager().getIP(targetUUID);
        if (ip == null) {
            ip = plugin.getLanguageManager().getMessage("account.no-data");
        }

        String regDate = pdm.getString(targetUUID, "account.registered-at");
        if (regDate == null) {
            regDate = plugin.getLanguageManager().getMessage("general.player-not-found");
        }

        String loginDate = pdm.getString(targetUUID, "account.last-login-at");
        if (loginDate == null) {
            loginDate = plugin.getLanguageManager().getMessage("general.player-not-found");
        }

        boolean isRegistered = plugin.getPasswordManager().isRegistered(targetUUID);
        boolean has2FA = plugin.getTwoFactorManager().has2FA(targetUUID);

        if (!isRegistered) {
            regDate = plugin.getLanguageManager().getMessage("account.status.not-registered");
        }

        String statusText2FA = has2FA ?
                plugin.getLanguageManager().getMessage("account.status.active-2fa") :
                plugin.getLanguageManager().getMessage("account.status.not-active-2fa");

        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.header").replace("%target%", targetName));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.uuid").replace("%uuid%", targetUUIDString));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.ip").replace("%ip%", ip));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.register-date").replace("%register_date%", regDate));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.login-date").replace("%login_date%", loginDate));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.status-2fa").replace("%status%", statusText2FA));
        sender.sendMessage(plugin.getLanguageManager().getMessage("account.stats.footer"));

        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);

        return true;
    }
}