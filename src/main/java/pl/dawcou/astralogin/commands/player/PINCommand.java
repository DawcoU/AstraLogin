package pl.dawcou.astralogin.commands.player;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.passwords.PINManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PINCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final PINManager pinManager;

    public PINCommand(AstraLogin plugin, PINManager pinManager) {
        this.plugin = plugin;
        this.pinManager = pinManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujpin") || command.getName().equalsIgnoreCase("resetpin")) {
            if (!sender.hasPermission("astralogin.resetpin")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.usage"));
                return true;
            }

            String inputName = args[0];
            UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);

            if (targetUUID == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found"));
                return true;
            }

            String targetName = plugin.getAccountManager().getRegisteredNameIgnoreCase(inputName);
            if (targetName == null) {
                targetName = inputName;
            }

            if (!pinManager.hasPIN(targetUUID)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.no-has-pin"));
                return true;
            }

            pinManager.deletePIN(targetUUID);

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-pin.admin-success")
                    .replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset PIN for player " + targetName);

            Player targetPlayer = Bukkit.getPlayer(targetUUID);

            if (targetPlayer != null) {
                targetPlayer.sendMessage(
                        plugin.getLanguageManager().getWithPrefix("reset-pin.player-message")
                );
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("pin")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length < 1) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
                return true;
            }

            String action = args[0];

            if (action.equalsIgnoreCase("setup")) {
                String method = plugin.getConfig().getString("features.pin.method", "RANDOM");
                UUID playerUUID = p.getUniqueId();

                if ("RANDOM".equalsIgnoreCase(method)) {
                    if (args.length != 1) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
                        return true;
                    }

                    if (pinManager.hasPIN(playerUUID)) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                        return true;
                    }

                    int length = plugin.getConfig().getInt("features.pin.length", 6);
                    length = Math.max(4, length);
                    length = Math.min(9, length);

                    String generatedPIN = pinManager.generatePIN(length);

                    plugin.getSchedulerManager().runAsync(() -> {
                        String hashedPIN = plugin.getPasswordManager().getPasswordHasher().hashPassword(generatedPIN);

                        pinManager.savePIN(playerUUID, hashedPIN);

                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-success")
                                    .replace("%pin%", generatedPIN));

                            plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                        });
                    });
                } else if ("TYPING".equalsIgnoreCase(method)) {
                    if (args.length != 2) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-usage"));
                        return true;
                    }

                    String PIN = args[1];

                    if (pinManager.hasPIN(playerUUID)) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                        return true;
                    }

                    int length = plugin.getConfig().getInt("features.pin.length", 6);
                    length = Math.max(4, length);
                    length = Math.min(9, length);

                    if (!PIN.matches("\\d+")) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.contains-letters"));
                        return true;
                    }

                    if (PIN.length() != length) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.invalid-length")
                                .replace("%length%", String.valueOf(length)));
                        return true;
                    }

                    plugin.getSchedulerManager().runAsync(() -> {
                        String hashedPIN = plugin.getPasswordManager().getPasswordHasher().hashPassword(PIN);

                        pinManager.savePIN(playerUUID, hashedPIN);

                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.typed-success"));

                            plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                        });
                    });
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("pin")) {
            if (args.length == 1) {
                hints.add("setup");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}