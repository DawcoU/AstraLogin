package pl.dawcou.astralogin.commands.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.ip.IPBanManager;
import pl.dawcou.astralogin.auth.security.ip.IPManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class IPManagerCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public IPManagerCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.usage"));
            return true;
        }

        String action = args[0].toLowerCase();

        if (!sender.hasPermission("astralogin.ipmanager." + action)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            return true;
        }

        String targetName = args[1];
        UUID targetUuid = plugin.getAccountManager().getUuidByUsername(targetName);
        String uuid = targetUuid.toString();

        IPManager ipManager = plugin.getIPManager();
        IPBanManager banManager = ipManager.getIpBanManager();
        String playerIp = ipManager.getIP(uuid);

        switch (action) {
            case "info":
                if (playerIp == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.no-ip")
                            .replace("%player%", targetName));
                    return true;
                }

                if (!banManager.isIPBanned(playerIp)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.info-not-banned")
                            .replace("%player%", targetName)
                            .replace("%ip%", playerIp));
                } else {
                    long timeLeft = banManager.getIPBanTimeLeft(playerIp);
                    String reason = banManager.getBanReason(playerIp);
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.info-banned")
                            .replace("%player%", targetName)
                            .replace("%ip%", playerIp)
                            .replace("%time%", String.valueOf(timeLeft))
                            .replace("%reason%", reason));
                }
                break;

            case "unban":
                if (playerIp == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.no-ip")
                            .replace("%player%", targetName));
                    return true;
                }

                if (!banManager.isIPBanned(playerIp)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.not-banned")
                            .replace("%player%", targetName));
                    return true;
                }

                banManager.unbanIP(playerIp);
                plugin.getLogManager().log("IP ban was removed for player " + targetName + " (IP: " + playerIp + ") by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.unban-success")
                        .replace("%player%", targetName)
                        .replace("%ip%", playerIp));
                break;

            case "bypass":
                if (banManager.hasBypass(uuid)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass-active")
                            .replace("%player%", targetName));
                    return true;
                }

                banManager.setBypass(uuid, true);
                plugin.getLogManager().log("IP bypass was enabled for player " + targetName + " by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass-enabled")
                        .replace("%player%", targetName));
                break;

            case "unbypass":
                if (!banManager.hasBypass(uuid)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass-not-active")
                            .replace("%player%", targetName));
                    return true;
                }

                // 1. Zdejmujemy bypass
                banManager.setBypass(uuid, false);
                plugin.getLogManager().log("IP bypass was disabled for player " + targetName + " by " + sender.getName());

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.bypass-disabled")
                        .replace("%player%", targetName));

                // 2. Pobieramy gracza online (jeśli gracz jest offline, getPlayerExact zwróci null)
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                String savedIP = ipManager.getIP(uuid);

                if (plugin.getConfig().getBoolean("security.ip-security.enabled", true) && onlineTarget != null) {
                    String currentIp = onlineTarget.getAddress().getAddress().getHostAddress();

                    if (savedIP != null && !ipManager.checkIP(uuid, savedIP, currentIp)) {

                        plugin.getLogManager().log("Player " + targetName + " was kicked after unbypass (IP Mismatch). Current IP: " + currentIp + ", Saved IP: " + savedIP);
                        plugin.getIpTrustManager().addTrustScore(
                                currentIp,
                                plugin.getIpTrustManager().getUnknownIpLoginPoints()
                        );

                        String kickMessage = plugin.getLanguageManager().getMessage("security.ip-mismatch");
                        onlineTarget.kickPlayer(kickMessage);
                    }
                }
                break;

            default:
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("ip-manager.usage"));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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