package pl.dawcou.astralogin.commands.pin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.password.PINManager;
import pl.dawcou.astralogin.system.SoundManager;

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
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (p == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        if (args.length < 1) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            return true;
        }

        String action = args[0];

        if (action.equalsIgnoreCase("setup")) {
            String method = plugin.getConfig().getString("features.pin.method", "RANDOM");
            UUID playerUUID = p.getUniqueId();

            if ("RANDOM".equalsIgnoreCase(method)) {
                if (args.length != 1) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.usage"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (pinManager.hasPIN(playerUUID)) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                int length = plugin.getConfig().getInt("features.pin.length", 6);
                length = Math.max(4, length);
                length = Math.min(9, length);

                String generatedPIN = pinManager.generatePIN(length);

                plugin.getSchedulerManager().runAsync(() -> {
                    String hashedPIN = plugin.getPasswordManager().getPasswordHasher().hashPassword(generatedPIN).hash();

                    pinManager.savePIN(playerUUID, hashedPIN);

                    plugin.getSchedulerManager().runSync(() -> {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-success")
                                .replace("%pin%", generatedPIN));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);

                        plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                    });
                });

            } else if ("TYPING".equalsIgnoreCase(method)) {
                if (args.length != 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.set-usage"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                String PIN = args[1];

                if (pinManager.hasPIN(playerUUID)) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.already-set"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                int length = plugin.getConfig().getInt("features.pin.length", 6);
                length = Math.max(4, length);
                length = Math.min(9, length);

                if (!PIN.matches("\\d+")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.contains-letters"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                    return true;
                }

                if (PIN.length() != length) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.invalid-length")
                            .replace("%length%", String.valueOf(length)));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                    return true;
                }

                plugin.getSchedulerManager().runAsync(() -> {
                    String hashedPIN = plugin.getPasswordManager().getPasswordHasher().hashPassword(PIN).hash();

                    pinManager.savePIN(playerUUID, hashedPIN);

                    plugin.getSchedulerManager().runSync(() -> {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("pin.typed-success"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);

                        plugin.getLogManager().log("Player " + p.getName() + " has registered his PIN code");
                    });
                });
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String[] args) {
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