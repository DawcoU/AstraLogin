package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IPManager {

    private final File file;
    private final FileConfiguration config;
    private final AstraLogin plugin;

    // Mapy do ochrony przed spamem wejść (IP-Spam)
    private final Map<String, Integer> ipAttempts = new HashMap<>();
    private final Map<String, Long> ipBans = new HashMap<>();
    private final HashMap<String, String> banReasons = new HashMap<>();

    public IPManager(AstraLogin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdir();

        File dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) dataDir.mkdir();

        this.file = new File(dataDir, "ips.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // --- TWOJE METODY (ZOSTAWIONE I NIETKNIĘTE) ---

    public void zapiszIP(String uuid, String ip) {
        config.set("ips." + uuid, ip);
        save();
    }

    public String getIP(String uuid) {
        return config.getString("ips." + uuid);
    }

    public void usunIP(String uuid) {
        config.set("ips." + uuid, null);
        save();
    }

    public int getIloscKontByIP(String ip) {
        if (config.getConfigurationSection("ips") == null) return 0;
        int count = 0;
        for (String key : config.getConfigurationSection("ips").getKeys(false)) {
            if (ip.equals(config.getString("ips." + key))) {
                count++;
            }
        }
        return count;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- NOWA OCHRONA IP (BOTY / SPAM WEJŚĆ) ---

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
        // 1. Ścieżka do configu (upewnij się, że w config.yml masz takie same nazwy!)
        String path = "security.ip-security.entry-protection.";

        int max = plugin.getConfig().getInt(path + "max-attempts", 5);
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "10 minutes");

        // 2. Liczymy próbę
        int current = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, current);

        // 3. Sprawdzamy limit
        if (current >= max) {
            // Zamieniamy tekst typu "10 minutes" na milisekundy
            long banMillis = plugin.getAttemptManager().parseTime(timeStr);

            // Nakładamy bana (używamy Twojej metody z IPManagera)
            banIPWithMillis(ip, banMillis, "SPAM");

            // CZYŚCIMY próby, żeby po odbanowaniu licznik startował od zera!
            ipAttempts.remove(ip);
        }
    }

    public long getIPBanTimeLeft(String ip) {
        if (!ipBans.containsKey(ip)) return 0;
        return (ipBans.get(ip) - System.currentTimeMillis()) / 1000;
    }

    public void resetIPAttempts(String ip) {
        ipAttempts.remove(ip);
        ipBans.remove(ip);
        banReasons.remove(ip);
    }
}