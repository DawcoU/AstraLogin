package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FilesUpdater {
    private final AstraLogin plugin;

    public FilesUpdater(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        // Najpierw aktualizujemy config.yml
        updateFile("config.yml");

        // Teraz aktualizujemy WSZYSTKIE wspierane języki
        // Dodaj tutaj skróty wszystkich języków, które masz w folderze languages w .jar
        List<String> supportedLangs = List.of("pl", "en");

        for (String lang : supportedLangs) {
            updateLanguageFile(lang);
        }
    }

    private void updateFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) return;

        InputStream defStream = plugin.getResource(fileName);
        if (defStream == null) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                if (fileName.equals("config.yml")) {
                    config.set("config-version", plugin.getDescription().getVersion());
                }
                config.save(file);
                if (fileName.equals("config.yml")) plugin.reloadConfig();
                plugin.getNoticeManager().sendConfigUpdateNotice();
            } catch (Exception e) {
                plugin.getNoticeManager().sendConfigErrorNotice(e.getMessage());
            }
        }
    }

    private void updateLanguageFile(String lang) {
        String fileName = "languages/" + lang + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);

        // Jeśli gracz nie ma jeszcze pliku na dysku, nie aktualizujemy go tutaj
        // (LanguageManager sam go wypakuje przy starcie)
        if (!langFile.exists()) return;

        InputStream defLangStream = plugin.getResource(fileName);
        if (defLangStream == null) return;

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        FileConfiguration defaultLangConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defLangStream, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaultLangConfig.getKeys(true)) {
            // SPRAWDZANIE: Czy klucz istnieje i czy nie jest pusty
            if (!langConfig.contains(key)) {
                langConfig.set(key, defaultLangConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                langConfig.save(langFile);
                plugin.getNoticeManager().sendLangUpdateSuccess(fileName);
            } catch (Exception e) {
                plugin.getNoticeManager().sendLangUpdateError(fileName, e.getMessage());
            }
        }
    }
}