package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IPManager {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    // Potężna optymalizacja: gotowe mapy do sprawdzania IP i ilości kont
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
        return uuidToIpCache.get(uuid); // Błyskawiczne pobieranie z RAM-u
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

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String path = "security.ip-security.entry-protection.";

        int max = plugin.getConfig().getInt(path + "max-attempts", 5);
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "10 minutes");

        int current = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, current);

        if (current >= max) {
            long banMillis = LoginUtils.parseTime(timeStr, 600000L);
            banIPWithMillis(ip, banMillis, "SPAM");
            ipAttempts.remove(ip);
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

        // Przebudowanie całego cache w RAM-ie przy przeładowaniu
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
}