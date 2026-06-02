package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PasswordManager {

    private final File file;
    private FileConfiguration config;

    // Cache w RAM-ie zapewniający natychmiastowy dostęp do haseł
    private final Map<String, String> passwordCache = new HashMap<>();

    public PasswordManager(AstraLogin plugin) {
        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.file = new File(dataDir, "passwords.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    public void savePassword(String uuid, String haslo) {
        passwordCache.put(uuid, haslo); // Błyskawiczny zapis do RAM-u
        config.set("passwords." + uuid, haslo);
        save();
    }

    public String getPassword(String uuid) {
        return passwordCache.get(uuid);
    }

    public boolean hasPassword(String uuid) {
        return passwordCache.containsKey(uuid);
    }

    public void deletePassword(String uuid) {
        passwordCache.remove(uuid);
        config.set("passwords." + uuid, null);
        save();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        this.passwordCache.clear();

        // Ładujemy wszystkie hasła do pamięci RAM przy starcie/przeładowaniu
        if (config.getConfigurationSection("passwords") != null) {
            for (String key : config.getConfigurationSection("passwords").getKeys(false)) {
                this.passwordCache.put(key, config.getString("passwords." + key));
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

    public static String hashPassword(String password) {
        int cost = AstraLogin.getInstance().getConfig().getInt("security.bcrypt.cost", 10);

        // Walidacja kosztu
        if (cost < 8 || cost > 16) {
            cost = 10;
        }

        try {
            return BCrypt.hashpw(password, BCrypt.gensalt(cost));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean verifyPassword(String password, String hashed) {
        try {
            if (hashed == null || !hashed.startsWith("$2a$")) return false;
            return BCrypt.checkpw(password, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}