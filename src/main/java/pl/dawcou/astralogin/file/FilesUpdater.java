package pl.dawcou.astralogin.file;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FilesUpdater {
    private final AstraLogin plugin;

    public FilesUpdater(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        updateFile("config.yml");

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

        // 1. DODAWANIE: Tylko liście (wartości końcowe)
        for (String key : defaultConfig.getKeys(true)) {
            if (defaultConfig.isConfigurationSection(key)) continue; // Pomijamy same nagłówki/sekcje!

            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        // 2. USUWANIE: Tylko liście, których nie ma już w jarze
        List<String> keysToRemove = new ArrayList<>();
        for (String key : config.getKeys(true)) {
            if (config.isConfigurationSection(key)) continue;

            if (!defaultConfig.contains(key)) {
                keysToRemove.add(key);
            }
        }

        if (!keysToRemove.isEmpty()) {
            for (String key : keysToRemove) {
                config.set(key, null);
            }
            changed = true;
        }

        if (changed) {
            try {
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

        if (!langFile.exists()) return;

        InputStream defLangStream = plugin.getResource(fileName);
        if (defLangStream == null) return;

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        FileConfiguration defaultLangConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defLangStream, StandardCharsets.UTF_8));

        boolean changed = false;

        // 1. DODAWANIE nowych wiadomości (tylko pojedyncze klucze/listy, nie całe sekcje)
        for (String key : defaultLangConfig.getKeys(true)) {
            if (defaultLangConfig.isConfigurationSection(key)) continue;

            if (!langConfig.contains(key)) {
                langConfig.set(key, defaultLangConfig.get(key));
                changed = true;
            }
        }

        // 2. USUWANIE nieużywanych wiadomości
        List<String> langKeysToRemove = new ArrayList<>();
        for (String key : langConfig.getKeys(true)) {
            if (langConfig.isConfigurationSection(key)) continue;

            if (!defaultLangConfig.contains(key)) {
                langKeysToRemove.add(key);
            }
        }

        if (!langKeysToRemove.isEmpty()) {
            for (String key : langKeysToRemove) {
                langConfig.set(key, null);
            }
            changed = true;
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