package pl.dawcou.astralogin.commands.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AstraLoginCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public AstraLoginCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {

            // Brak argumentów lub komenda /al help /al pomoc
            if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("pomoc")))) {
                plugin.getNoticeManager().sendHelp(sender);
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }

            // Komenda /al reload
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                plugin.reload();

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.reload-success"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }

            // Komenda /al info
            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                String prefix = (sender instanceof ConsoleCommandSender) ? AstraLogin.PREFIX2 : AstraLogin.PREFIX;

                sender.sendMessage(plugin.getLanguageManager().parseToLegacy("<gray>------------ " + prefix + " <gray>----------"));
                sender.sendMessage("§aPlugin created by: §e " + plugin.getAuthor());
                sender.sendMessage("§aPlugin version: §ev" + plugin.getDescription().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 " + plugin.getAuthor() + " All rights reserved");
                sender.sendMessage("§7-----------------------");

                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }
        }

        if (p != null) {
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("astralogin") || cmd.equalsIgnoreCase("al")) {
            if (args.length == 1) {
                hints.add("info");
                hints.add("help");
                if (sender.hasPermission("astralogin.reload")) hints.add("reload");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}