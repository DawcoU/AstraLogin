package pl.dawcou.astralogin.commands.account;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IPListCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public IPListCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (!sender.hasPermission("astralogin.iplist")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-list.generating"));

        plugin.getSchedulerManager().runAsync(() -> {
            Map<String, List<String>> ipToNamesMap = plugin.getIPManager().getIpToNamesMap();

            if (ipToNamesMap.isEmpty()) {
                plugin.getSchedulerManager().runSync(() -> {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-list.empty"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                });
                return;
            }

            String format = plugin.getLanguageManager().getMessage("ip-list.format");
            List<String> formattedEntries = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : ipToNamesMap.entrySet()) {
                formattedEntries.add(format
                        .replace("%ip%", entry.getKey())
                        .replace("%count%", String.valueOf(entry.getValue().size()))
                        .replace("%players%", String.join(", ", entry.getValue())));
            }

            plugin.getSchedulerManager().runSync(() -> {
                sender.sendMessage(plugin.getLanguageManager().getMessage("ip-list.header"));

                for (String line : formattedEntries) {
                    sender.sendMessage(line);
                }

                sender.sendMessage(plugin.getLanguageManager().getMessage("ip-list.footer"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
            });
        });

        return true;
    }
}