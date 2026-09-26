package pl.dawcou.astralogin.commands.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;
import pl.dawcou.astralogin.system.utils.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IPTrustCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final IPTrustManager ipTrustManager;

    public IPTrustCommand(AstraLogin plugin, IPTrustManager ipTrustManager) {
        this.plugin = plugin;
        this.ipTrustManager = ipTrustManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zaufanieip") || command.getName().equalsIgnoreCase("iptrust")) {
            if (args.length == 0) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            String action = args[0];

            // Sprawdzamy jaką operację wpisano
            if (action.equalsIgnoreCase("reset")) {
                if (args.length != 2) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.reset")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                String ip = args[1];

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!ipTrustManager.hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                // Resetujemy zaufanie IP
                ipTrustManager.resetTrustIP(ip);

                String successMsg = plugin.getLanguageManager().getWithPrefix("ip-trust.reset-success")
                        .replace("%ip%", args[1]);

                sender.sendMessage(successMsg);
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }

                String adminName = sender.getName();

                plugin.getLogManager().log("Admin " + adminName + " reset IP trust: " + ip);
                return true;

            } else if (action.equalsIgnoreCase("set")) {
                if (args.length != 3) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.set")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                String ip = args[1];
                String scoreArg = args[2];

                int score;

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!ipTrustManager.hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                try {
                    score = Integer.parseInt(scoreArg);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.invalid-number"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                ipTrustManager.setTrustScore(ip, score);

                String successMsg = plugin.getLanguageManager().getWithPrefix("ip-trust.set-success")
                        .replace("%ip%", args[1])
                        .replace("%score%", String.valueOf(score));

                sender.sendMessage(successMsg);
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;

            } else if (action.equalsIgnoreCase("info")) {
                if (args.length != 2) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!sender.hasPermission("astralogin.iptrust.info")) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                String ip = args[1];

                if (ip == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }
                if (!ipTrustManager.hasIP(ip)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.ip-not-found")
                            .replace("%ip%", ip));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                int score = ipTrustManager.getTrustScore(ip);
                IPTrustManager.TrustLevel level = ipTrustManager.getTrustLevel(ip);
                String rate;

                switch (level) {
                    case FATAL:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-fatal");
                        break;

                    case BAD:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-bad");
                        break;

                    case GOOD:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-good");
                        break;

                    case IDEAL:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-ideal");
                        break;

                    default:
                        rate = plugin.getLanguageManager().getMessage("ip-trust.rate-neutral");
                }

                List<String> infoLines = plugin.getLanguageManager().getMessageList("ip-trust.ip-info");

                for (String line : infoLines) {
                    sender.sendMessage(line
                            .replace("%ip%", ip)
                            .replace("%score%", String.valueOf(score))
                            .replace("%rate%", rate));
                }
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-trust.usage"));
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("zaufanieip") || cmd.equalsIgnoreCase("iptrust")) {
            if (args.length == 1) {
                if (sender.hasPermission("astralogin.iptrust.reset")) {
                    hints.add("reset");
                }
                if (sender.hasPermission("astralogin.iptrust.info")) {
                    hints.add("info");
                }
                if (sender.hasPermission("astralogin.iptrust.set")) {
                    hints.add("set");
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("info"))) {
                hints.add("<IP>");
            } else if (args.length == 3 && (args[0].equalsIgnoreCase("set"))) {
                hints.add("<score>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}