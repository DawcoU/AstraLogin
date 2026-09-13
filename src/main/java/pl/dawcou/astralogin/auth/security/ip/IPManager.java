package pl.dawcou.astralogin.auth.security.ip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//--------------------------------------------------
// IPManager - zarzadzanie plikami i cache IP
//--------------------------------------------------
public class IPManager {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;
    private final IPBanManager ipBanManager;

    public static int ipCheckOctets = 4;

    private final Map<String, String> uuidToIpCache = new HashMap<>();
    private final Map<String, Integer> ipCountCache = new HashMap<>();

    public Map<String, String> getUuidToIpCache() {
        return uuidToIpCache;
    }

    public IPBanManager getIpBanManager() {
        return ipBanManager;
    }

    public IPManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.ipBanManager = new IPBanManager(plugin);

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        file = new File(dataDir, "ips.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    public boolean checkIP(String uuid, String savedIP, String currentIP) {
        if (uuid != null && ipBanManager.hasBypass(uuid)) {
            return true;
        }
        return isValidIP(savedIP, currentIP);
    }

    public boolean isValidIP(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equals(currentIP)) return true;

        if (savedIP.contains(".") && currentIP.contains(".")) {
            String[] s = savedIP.split("\\.");
            String[] c = currentIP.split("\\.");

            if (s.length < 4 || c.length < 4) {
                return false;
            }

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
        String oldIp = uuidToIpCache.get(uuid);
        if (oldIp != null && ipCountCache.containsKey(oldIp)) {
            ipCountCache.put(oldIp, Math.max(0, ipCountCache.get(oldIp) - 1));
        }

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

    public boolean isIPBanned(String ip) {
        return ipBanManager.isIPBanned(ip);
    }

    public String getBanReason(String ip) {
        return ipBanManager.getBanReason(ip);
    }

    public void banIPWithMillis(String ip, long durationMillis, String reason) {
        String uuid = null;
        for (Map.Entry<String, String> entry : uuidToIpCache.entrySet()) {
            if (entry.getValue().equals(ip)) {
                uuid = entry.getKey();
                break;
            }
        }
        ipBanManager.banIPWithMillis(ip, durationMillis, reason, uuid);
    }

    public void addIPAttempt(String ip) {
        String uuid = null;
        for (Map.Entry<String, String> entry : uuidToIpCache.entrySet()) {
            if (entry.getValue().equals(ip)) {
                uuid = entry.getKey();
                break;
            }
        }
        ipBanManager.addIPAttempt(ip, uuid);
    }

    public long getIPBanTimeLeft(String ip) {
        return ipBanManager.getIPBanTimeLeft(ip);
    }

    public void resetIPAttempts(String ip) {
        ipBanManager.resetIPAttempts(ip);
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);

        uuidToIpCache.clear();
        ipCountCache.clear();

        if (config.getConfigurationSection("ips") != null) {
            for (String key : config.getConfigurationSection("ips").getKeys(false)) {
                String ip = config.getString("ips." + key);
                if (ip != null) {
                    uuidToIpCache.put(key, ip);
                    ipCountCache.put(ip, ipCountCache.getOrDefault(ip, 0) + 1);
                }
            }
        }

        ipBanManager.loadBans();
    }

    private void save() {
        synchronized (config) {
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}