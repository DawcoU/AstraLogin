package pl.dawcou.astralogin.auth.security.ip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IPBanManager {

    private final AstraLogin plugin;
    private final File bansFile;
    private FileConfiguration bansConfig;

    private final Map<String, Integer> ipAttempts = new HashMap<>();
    private final Map<String, Long> ipBans = new HashMap<>();
    private final Map<String, String> banReasons = new HashMap<>();
    private final Map<String, String> ipBannedUuid = new HashMap<>();

    // Zbiór przechowywujący UUID graczy z bypassem ochrony IP
    private final Set<String> ipBypassUuids = new HashSet<>();

    public IPBanManager(AstraLogin plugin) {
        this.plugin = plugin;

        File globalDir = new File(plugin.getDataFolder(), "data/global");
        if (!globalDir.exists()) {
            globalDir.mkdirs();
        }

        bansFile = new File(globalDir, "ip_bans.yml");
        if (!bansFile.exists()) {
            try {
                bansFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        loadBans();
    }

    public boolean isIPBanned(String ip) {
        if (!ipBans.containsKey(ip)) return false;
        if (System.currentTimeMillis() > ipBans.get(ip)) {
            unbanIP(ip);
            return false;
        }
        return true;
    }

    public String getBanReason(String ip) {
        return banReasons.getOrDefault(ip, "UNKNOWN");
    }

    public void banIPWithMillis(String ip, long durationMillis, String reason, String uuid) {
        ipBans.put(ip, System.currentTimeMillis() + durationMillis);
        banReasons.put(ip, reason);
        if (uuid != null) {
            ipBannedUuid.put(ip, uuid);
        }
        saveBans();
    }

    public void unbanIP(String ip) {
        ipAttempts.remove(ip);
        ipBans.remove(ip);
        banReasons.remove(ip);
        ipBannedUuid.remove(ip);
        saveBans();
    }

    public void addIPAttempt(String ip, String uuid) {
        String path = "security.anti-spam.";

        int max = plugin.getConfig().getInt(path + "max-attempts", 5);
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "30 minutes");

        int current = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, current);

        if (current >= max) {
            long banMillis = TimeUtils.parseTime(timeStr, 600000L);
            banIPWithMillis(ip, banMillis, "SPAM", uuid);
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
        unbanIP(ip);
    }

    // --- LOGIKA BYPASS IP ---
    public boolean hasBypass(String uuid) {
        return ipBypassUuids.contains(uuid);
    }

    public void setBypass(String uuid, boolean status) {
        if (status) {
            ipBypassUuids.add(uuid);
        } else {
            ipBypassUuids.remove(uuid);
        }
        saveBans();
    }

    public void saveBans() {
        synchronized (bansFile) {
            bansConfig = new YamlConfiguration();
            long now = System.currentTimeMillis();

            for (Map.Entry<String, Long> entry : ipBans.entrySet()) {
                String ip = entry.getKey();
                long expireTime = entry.getValue();

                if (expireTime > now) {
                    String cleanIp = ip.replace(".", "_");
                    bansConfig.set("bans." + cleanIp + ".ip", ip);
                    bansConfig.set("bans." + cleanIp + ".expire", expireTime);
                    bansConfig.set("bans." + cleanIp + ".reason", banReasons.getOrDefault(ip, "UNKNOWN"));
                    bansConfig.set("bans." + cleanIp + ".uuid", ipBannedUuid.getOrDefault(ip, "UNKNOWN"));
                }
            }

            bansConfig.set("bypasses", new java.util.ArrayList<>(ipBypassUuids));

            try {
                bansConfig.save(bansFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadBans() {
        bansConfig = YamlConfiguration.loadConfiguration(bansFile);
        ipBans.clear();
        banReasons.clear();
        ipBannedUuid.clear();
        ipBypassUuids.clear();

        if (bansConfig.getConfigurationSection("bans") != null) {
            long now = System.currentTimeMillis();
            for (String key : bansConfig.getConfigurationSection("bans").getKeys(false)) {
                String path = "bans." + key + ".";
                String ip = bansConfig.getString(path + "ip");
                long expire = bansConfig.getLong(path + "expire");
                String reason = bansConfig.getString(path + "reason");
                String uuid = bansConfig.getString(path + "uuid");

                if (ip != null && expire > now) {
                    ipBans.put(ip, expire);
                    if (reason != null) banReasons.put(ip, reason);
                    if (uuid != null) ipBannedUuid.put(ip, uuid);
                }
            }
        }

        if (bansConfig.isList("bypasses")) {
            ipBypassUuids.addAll(bansConfig.getStringList("bypasses"));
        }
    }
}