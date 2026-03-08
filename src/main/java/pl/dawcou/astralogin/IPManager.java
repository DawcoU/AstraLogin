package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class IPManager {

    private final File file;
    private final FileConfiguration config;

    public IPManager(AstraLogin plugin) {
        // 1. Upewniamy się, że główny folder istnieje
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdir();

        // 2. Tworzymy folder 'playerdata'
        File dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) dataDir.mkdir();

        // 3. TUTAJ ZMIANA: używamy dataDir zamiast getDataFolder()
        this.file = new File(dataDir, "ips.yml");

        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void zapiszIP(String uuid, String ip) {
        config.set("ips." + uuid, ip);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public String getIP(String uuid) {
        return config.getString("ips." + uuid);
    }

    public void usunIP(String uuid) {
        config.set("ips." + uuid, null);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
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
}