package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FilesUpdater {

    private final AstraLogin plugin;

    public FilesUpdater(AstraLogin plugin) {
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
                plugin.sendConfigUpdateNotice();
            } catch (Exception e) {
                plugin.sendConfigErrorNotice(e.getMessage());
            }
        }
    }

    public void checkLanguages() {
        // 1. Sprawdzamy, jaki język jest ustawiony w głównym configu
        String lang = plugin.getConfig().getString("language", "pl");
        String fileName = "languages/" + lang + ".yml";

        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) return; // Jeśli pliku nie ma, LanguageManager i tak go stworzy

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 2. Pobieramy domyślny plik z wnętrza Twojego pluginu (.jar)
        InputStream defLangStream = plugin.getResource(fileName);
        if (defLangStream == null) {
            // Jeśli admin ustawił własny język (np. de.yml), którego nie masz w .jar,
            // to nie mamy z czym porównać, więc wychodzimy.
            return;
        }

        FileConfiguration defaultLangConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defLangStream, StandardCharsets.UTF_8));

        boolean changed = false;

        // 3. Porównujemy klucze i dopisujemy brakujące
        for (String key : defaultLangConfig.getKeys(true)) {
            if (!langConfig.contains(key)) {
                langConfig.set(key, defaultLangConfig.get(key));
                changed = true;
            }
        }

        // 4. Jeśli coś się zmieniło, zapisujemy plik
        if (changed) {
            try {
                langConfig.save(langFile);
                // Opcjonalnie: przeładuj managera języków, żeby od razu widział zmiany
                plugin.getLanguageManager().loadLanguage();

                // Możesz użyć wbudowanego loga, którego robiliśmy wcześniej:
                plugin.getLogger().info("§e[AstraLogin] §aZaktualizowano brakujące linijki w pliku " + fileName);
            } catch (Exception e) {
                plugin.getLogger().severe("§cNie można było zaktualizować pliku językowego: " + e.getMessage());
            }
        }
    }

}