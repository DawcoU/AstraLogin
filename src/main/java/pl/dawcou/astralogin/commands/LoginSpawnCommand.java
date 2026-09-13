package pl.dawcou.astralogin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoginSpawnCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public LoginSpawnCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("loginspawn") || command.getName().equalsIgnoreCase("spawnlogowania")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("setspawn")) {
                if (!p.hasPermission("astralogin.spawn.set")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.usage"));
                    return true;
                }

                String type = args[1].toLowerCase();
                SpawnType spawnType = SpawnType.parse(args[1]);

                // Sprawdzamy czy typ jest poprawny
                if (spawnType == null) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.invalid-type"));
                    return true;
                }

                // 1. LOGIKA POTWIERDZENIA NADPISANIA
                boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                if (plugin.getSpawnManager().hasSpawn(spawnType) && !confirmed) {
                    String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn.exists").replace("%type%", type);
                    String btnTextStr = plugin.getLanguageManager().getMessage("spawn.overwrite-button");
                    String hoverTextStr = plugin.getLanguageManager().getMessage("spawn.overwrite-hover").replace("%type%", type);

                    Component baseMsg = LegacyComponentSerializer.legacySection()
                            .deserialize(baseMsgStr + " ");

                    Component confirmBtn = LegacyComponentSerializer.legacySection()
                            .deserialize(btnTextStr)
                            .clickEvent(ClickEvent.runCommand("/loginspawn setspawn " + type + " confirm"))
                            .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                    plugin.getAdventure().player(p).sendMessage(baseMsg.append(confirmBtn));
                    return true;
                }

                // 2. WŁAŚCIWE USTAWIENIE SPAWNU
                plugin.getSpawnManager().setSpawn(spawnType, p);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.set-success").replace("%type%", type));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("delspawn")) {
                if (!p.hasPermission("astralogin.spawn.delete")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.usage"));
                    return true;
                }

                String type = args[1].toLowerCase();
                SpawnType spawnType = SpawnType.parse(args[1]);

                // 1. LOGIKA POTWIERDZENIA
                boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                if (confirmed) {
                    if (plugin.getSpawnManager().hasSpawn(spawnType)) {
                        plugin.getSpawnManager().delSpawn(spawnType);
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.deleted-success").replace("%type%", type));
                    } else {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.does-not-exist").replace("%type%", type));
                    }
                    return true;
                }

                // 2. SPRAWDZAMY CZY W OGÓLE ISTNIEJE
                if (!plugin.getSpawnManager().hasSpawn(spawnType)) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.does-not-exist").replace("%type%", type));
                    return true;
                }

                // 3. POKAZYWANIE PRZYCISKU
                String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn.delete-confirm").replace("%type%", type);
                String btnTextStr = plugin.getLanguageManager().getMessage("spawn.delete-button");
                String hoverTextStr = plugin.getLanguageManager().getMessage("spawn.delete-hover").replace("%type%", type);

                Component baseMsg = LegacyComponentSerializer.legacySection()
                        .deserialize(baseMsgStr + " ");

                Component confirmBtn = LegacyComponentSerializer.legacySection()
                        .deserialize(btnTextStr)
                        .clickEvent(ClickEvent.runCommand("/loginspawn delspawn " + type + " confirm"))
                        .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                plugin.getAdventure().player(p).sendMessage(baseMsg.append(confirmBtn));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.usage"));

            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("spawnlogowania") || cmd.equalsIgnoreCase("loginspawn")) {
            if (args.length == 1) {
                if (sender.hasPermission("astralogin.spawn.set")) {
                    hints.add("setspawn");
                }
                if (sender.hasPermission("astralogin.spawn.delete")) {
                    hints.add("delspawn");
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn"))) {
                hints.add("before_login");
                hints.add("after_login");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}