package pl.dawcou.astralogin.auth.security.passwords;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PasswordManager {

    private final AstraLogin plugin;
    private final PasswordHasher passwordHasher;

    private final File file;
    private FileConfiguration config;

    private final Map<String, String> passwordCache = new HashMap<>();

    public PasswordManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.passwordHasher = new PasswordHasher(plugin);

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

    public PasswordHasher getPasswordHasher() {
        return passwordHasher;
    }

    public void savePassword(String uuid, String password) {
        passwordCache.put(uuid, password);
        config.set("passwords." + uuid + ".password", password);
        save();
    }

    public String getPassword(String uuid) {
        return passwordCache.get(uuid);
    }

    public boolean isRegistered(String uuid) {
        return passwordCache.containsKey(uuid);
    }

    public void deletePassword(String uuid) {
        passwordCache.remove(uuid);
        config.set("passwords." + uuid + ".password", null);
        save();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        passwordCache.clear();

        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                String password = config.getString("passwords." + key + ".password");

                if (password != null) {
                    passwordCache.put(key, password);
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