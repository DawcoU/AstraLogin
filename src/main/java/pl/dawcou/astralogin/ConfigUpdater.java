package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigUpdater {

    private final AstraLogin plugin;

    public ConfigUpdater(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream == null) return;

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                config.set("config-version", plugin.getDescription().getVersion());
                config.save(configFile);
                plugin.reloadConfig();
                plugin.getLogger().info(AstraLogin.PREFIX2 + " §aPomyślnie dopisano brakujące linijki do configu");
            } catch (Exception e) {
                plugin.getLogger().severe("§cBłąd podczas zapisu configu: " + e.getMessage());
            }
        }
    }
}