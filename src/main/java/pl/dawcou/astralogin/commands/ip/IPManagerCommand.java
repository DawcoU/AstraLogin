package pl.dawcou.astralogin.commands.ip;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.auth.security.ip.IPBanManager;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class IPManagerCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public IPManagerCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.usage"));
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String action = args[0].toLowerCase();

        if (!sender.hasPermission("astralogin.ipmanager." + action)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String inputName = args[1];
        UUID targetUuid = plugin.getAccountManager().getUuidByUsername(inputName);

        if (targetUuid == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found")
                    .replace("%target%", inputName));
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String uuid = targetUuid.toString();

        IPManager ipManager = plugin.getIPManager();
        IPBanManager banManager = ipManager.getIpBanManager();
        String playerIp = ipManager.getIP(targetUuid);

        switch (action) {
            case "info":
                if (playerIp == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.no-ip")
                            .replace("%player%", inputName));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                if (!banManager.isIPBanned(playerIp)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.info.unban.not-banned")
                            .replace("%player%", inputName)
                            .replace("%ip%", playerIp));
                } else {
                    long timeLeft = banManager.getIPBanTimeLeft(playerIp);
                    AttemptType reason = banManager.getBanReason(playerIp);
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.info.banned")
                            .replace("%player%", inputName)
                            .replace("%ip%", playerIp)
                            .replace("%time%", String.valueOf(timeLeft))
                            .replace("%reason%", reason.name()));
                }
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                break;

            case "unban":
                if (playerIp == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.no-ip")
                            .replace("%player%", inputName));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                if (!banManager.isIPBanned(playerIp)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.unban.not-banned")
                            .replace("%player%", inputName));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                banManager.unbanIP(playerIp);
                plugin.getLogManager().log("IP ban was removed for player " + inputName + " (IP: " + playerIp + ") by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.unban.success")
                        .replace("%player%", inputName)
                        .replace("%ip%", playerIp));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                break;

            case "bypass":
                if (banManager.hasBypass(uuid)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass.already-enabled")
                            .replace("%player%", inputName));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                banManager.setBypass(uuid, true);
                plugin.getLogManager().log("IP bypass was enabled for player " + inputName + " by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass.enabled")
                        .replace("%player%", inputName));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                break;

            case "unbypass":
                if (!banManager.hasBypass(uuid)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass.already-disabled")
                            .replace("%player%", inputName));
                    if (p != null) {
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    }
                    return true;
                }

                // 1. Zdejmujemy bypass
                banManager.setBypass(uuid, false);
                plugin.getLogManager().log("IP bypass was disabled for player " + inputName + " by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass.disabled")
                        .replace("%player%", inputName));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }

                // 2. Pobieramy gracza online (jeśli gracz jest offline, getPlayerExact zwróci null)
                Player onlineTarget = Bukkit.getPlayerExact(inputName);
                String savedIP = ipManager.getIP(targetUuid);

                if (plugin.getConfig().getBoolean("security.ip-security.enabled", true) && onlineTarget != null) {
                    String currentIp = Objects.requireNonNull(onlineTarget.getAddress()).getAddress().getHostAddress();

                    if (savedIP != null && !ipManager.checkIP(targetUuid, savedIP, currentIp)) {

                        plugin.getLogManager().log("Player " + inputName + " was detected with an IP mismatch after unbypass. Current IP: " + currentIp + ", Saved IP: " + savedIP);
                        plugin.getIpTrustManager().changeTrustScore(
                                currentIp,
                                plugin.getIpTrustManager().getUnknownIpLoginPoints()
                        );

                        plugin.getSuspicionManager().incrementSuspicion(targetUuid);
                    }
                }
                break;

            default:
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.usage"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        List<String> hints = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("astralogin.ipmanager.info")) hints.add("info");
            if (sender.hasPermission("astralogin.ipmanager.unban")) hints.add("unban");
            if (sender.hasPermission("astralogin.ipmanager.bypass")) hints.add("bypass");
            if (sender.hasPermission("astralogin.ipmanager.unbypass")) hints.add("unbypass");
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (sender.hasPermission("astralogin.ipmanager." + action)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    hints.add(player.getName());
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}