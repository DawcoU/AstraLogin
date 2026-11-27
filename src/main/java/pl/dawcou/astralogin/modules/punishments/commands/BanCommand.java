package pl.dawcou.astralogin.modules.punishments.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.List;

public class BanCommand extends Command {

    private final AstraLogin plugin;

    public BanCommand(AstraLogin plugin) {
        super("ban", "", "", List.of("zbanuj"));

        this.plugin = plugin;
    }

    // --------------------------------------------------
    // Command Execution Logic
    // --------------------------------------------------

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.punish.ban")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        // Future command logic...
        return true;
    }
}