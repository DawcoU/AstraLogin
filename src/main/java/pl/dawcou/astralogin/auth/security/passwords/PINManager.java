package pl.dawcou.astralogin.auth.security.passwords;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class PINManager {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<String, String> PINCache = new HashMap<>();

    public PINManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        file = new File(dataDir, "passwords.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    public String generatePIN(int length) {
        int bound = (int) Math.pow(10, length);
        int pinNumber = ThreadLocalRandom.current().nextInt(bound);
        return String.format("%0" + length + "d", pinNumber);
    }

    public void savePIN(String uuid, String PIN) {
        PINCache.put(uuid, PIN);
        config.set("passwords." + uuid + ".pin", PIN);
        save();
    }

    public String getPIN(String uuid) {
        return PINCache.get(uuid);
    }

    public boolean hasPIN(String uuid) {
        return PINCache.containsKey(uuid);
    }

    public void deletePIN(String uuid) {
        PINCache.remove(uuid);
        config.set("passwords." + uuid, null);
        save();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        PINCache.clear();

        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                String pin = config.getString("passwords." + key + ".pin");

                if (pin != null) {
                    PINCache.put(key, pin);
                }
            }
        }
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