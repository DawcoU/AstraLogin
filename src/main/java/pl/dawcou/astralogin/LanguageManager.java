package pl.dawcou.astralogin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles(); // Najpierw upewniamy się, że pliki są na dysku
        reload();     // Potem ładujemy je do RAMu
    }

    public void reload() {
        // 1. Czyścimy mapę, żeby nie dublować przy przeładowaniu
        messages.clear();

        String lang = plugin.getConfig().getString("language", "pl");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "languages/pl.yml");
        }

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 2. Pobieramy sekcję "messages" z pliku YAML
        ConfigurationSection msgSection = langConfig.getConfigurationSection("messages");

        if (msgSection != null) {
            // Przechodzimy po wszystkich kluczach wewnątrz sekcji messages:
            for (String key : msgSection.getKeys(false)) {
                String msg = msgSection.getString(key);
                if (msg != null) {
                    // Wrzucamy do mapy pod samym kluczem (np. "copy-success")
                    // Dzięki temu getMessage("copy-success") to znajdzie!
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        } else {
            // Jeśli plik nie ma sekcji "messages:", czytamy wszystko z głównego poziomu
            for (String key : langConfig.getKeys(false)) {
                if (langConfig.isString(key)) {
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', langConfig.getString(key)));
                }
            }
        }
    }

    private void setupFiles() {
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();

        String[] defaultLangs = {"pl.yml", "en.yml"};
        for (String langFile : defaultLangs) {
            File file = new File(langFolder, langFile);
            if (!file.exists()) {
                plugin.saveResource("languages/" + langFile, false);
            }
        }
    }

    // Pobiera czystą wiadomość z mapy
    public String getMessage(String path) {
        return messages.getOrDefault(path, "§cMissing string: " + path);
    }

    public String getWithPrefix(String path) {
        return AstraLogin.PREFIX + " " + getMessage(path);
    }

    // Metoda z placeholderem (np. do {COUNT})
    public String getWithPrefix(String path, String placeholder, String value) {
        String msg = getMessage(path).replace(placeholder, value);
        return AstraLogin.PREFIX + " " + msg;
    }
}