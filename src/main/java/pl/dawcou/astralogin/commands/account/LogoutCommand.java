
package pl.dawcou.astralogin.commands.account;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;

public class LogoutCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public LogoutCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        plugin.getLoginSystem().logout(p);
        plugin.getLogManager().log("Player " + p.getName() + " has logged out");

        return true;
    }
}
