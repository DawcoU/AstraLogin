package pl.dawcou.astralogin.auth.security;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.system.LoginUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IPManager implements CommandExecutor {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    public static int ipCheckOctets = 4;

    // Mapy do sprawdzania IP i ilości kont
    private final Map<String, String> uuidToIpCache = new HashMap<>();
    private final Map<String, Integer> ipCountCache = new HashMap<>();

    // Mapy ochrony IP-Spam
    private final Map<String, Integer> ipAttempts = new HashMap<>();
    private final Map<String, Long> ipBans = new HashMap<>();
    private final Map<String, String> banReasons = new HashMap<>();

    public Map<String, String> getUuidToIpCache() {
        return this.uuidToIpCache;
    }

    public IPManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.file = new File(dataDir, "ips.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("zresetujip") || command.getName().equalsIgnoreCase("resetip")) {
            if (!sender.hasPermission("astralogin.resetip")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.usage"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            String uuid = target.getUniqueId().toString();

            // Sprawdzamy czy IP w ogóle istnieje
            if (getIP(uuid) == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-ip.no-ip"));
                return true;
            }

            // Usuwamy IP
            deleteIP(uuid);

            // Pobieramy obiekt zalogowanego gracza, jeśli jest na serwerze
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                String kickReason = plugin.getLanguageManager().getMessage("reset-ip.player-kick");
                onlineTarget.kick(Component.text(kickReason));
            }

            String successMsg = plugin.getLanguageManager().getWithPrefix("reset-ip.admin-success")
                    .replace("%player%", args[0]);

            sender.sendMessage(successMsg);

            String adminName = sender.getName();
            String targetName = target.getName() != null ? target.getName() : args[0];
            plugin.getLogManager().log("Admin " + adminName + " reset IP for player " + targetName);
            return true;
        }
        return false;
    }

    public static boolean CheckIP(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equals(currentIP)) return true;

        if (savedIP.contains(".") && currentIP.contains(".")) {
            String[] s = savedIP.split("\\.");
            String[] c = currentIP.split("\\.");

            if (s.length < 4 || c.length < 4) {
                return false;
            }

            // Korzystamy bezpośrednio z pola w tej klasie, zabezpieczając zakres
            int octets = ipCheckOctets;
            if (octets < 1) octets = 1;
            if (octets > 4) octets = 4;

            for (int i = 0; i < octets; i++) {
                if (!s[i].equals(c[i])) {
                    return false;
                }
            }
            return true;
        }

        return savedIP.equalsIgnoreCase(currentIP);
    }

    public void saveIP(String uuid, String ip) {
        // Jeśli gracz zmienia IP, zmniejszamy licznik starego IP
        String oldIp = uuidToIpCache.get(uuid);
        if (oldIp != null && ipCountCache.containsKey(oldIp)) {
            ipCountCache.put(oldIp, Math.max(0, ipCountCache.get(oldIp) - 1));
        }

        // Aktualizujemy cache nowymi danymi
        uuidToIpCache.put(uuid, ip);
        ipCountCache.put(ip, ipCountCache.getOrDefault(ip, 0) + 1);

        config.set("ips." + uuid, ip);
        save();
    }

    public String getIP(String uuid) {
        return uuidToIpCache.get(uuid);
    }

    public void deleteIP(String uuid) {
        String ip = uuidToIpCache.remove(uuid);
        if (ip != null && ipCountCache.containsKey(ip)) {
            ipCountCache.put(ip, Math.max(0, ipCountCache.get(ip) - 1));
        }
        config.set("ips." + uuid, null);
        save();
    }

    public int getNumberOfAccountsByIP(String ip) {
        return ipCountCache.getOrDefault(ip, 0);
    }

    // --- OCHRONA IP ---
    public boolean isIPBanned(String ip) {
        if (!ipBans.containsKey(ip)) return false;
        if (System.currentTimeMillis() > ipBans.get(ip)) {
            ipBans.remove(ip);
            banReasons.remove(ip);
            ipAttempts.remove(ip);
            return false;
        }
        return true;
    }

    public String getBanReason(String ip) {
        return banReasons.getOrDefault(ip, "UNKNOWN");
    }

    public void banIPWithMillis(String ip, long durationMillis, String reason) {
        ipBans.put(ip, System.currentTimeMillis() + durationMillis);
        banReasons.put(ip, reason);
    }

    public void addIPAttempt(String ip) {
        String path = "security.anti-spam.";

        int max = plugin.getConfig().getInt(path + "max-attempts", 5);
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "30 minutes");

        int current = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, current);

        if (current >= max) {
            long banMillis = LoginUtils.parseTime(timeStr, 600000L);
            banIPWithMillis(ip, banMillis, "SPAM");
            ipAttempts.remove(ip);
            plugin.getIpTrustManager().addTrustScore(
                    ip,
                    plugin.getIpTrustManager().getIpSpamPoints()
            );
        }
    }

    public long getIPBanTimeLeft(String ip) {
        if (!ipBans.containsKey(ip)) return 0;
        long timeLeft = (ipBans.get(ip) - System.currentTimeMillis()) / 1000;
        return Math.max(0, timeLeft);
    }

    public void resetIPAttempts(String ip) {
        ipAttempts.remove(ip);
        ipBans.remove(ip);
        banReasons.remove(ip);
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);

        this.uuidToIpCache.clear();
        this.ipCountCache.clear();

        if (config.getConfigurationSection("ips") != null) {
            for (String key : config.getConfigurationSection("ips").getKeys(false)) {
                String ip = config.getString("ips." + key);
                if (ip != null) {
                    this.uuidToIpCache.put(key, ip);
                    this.ipCountCache.put(ip, this.ipCountCache.getOrDefault(ip, 0) + 1);
                }
            }
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}