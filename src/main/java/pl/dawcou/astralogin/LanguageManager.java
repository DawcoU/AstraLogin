package pl.dawcou.astralogin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles(); // Najpierw upewniamy się, że pliki są na dysku
        reload();     // Potem ładujemy je do RAMu
    }

    public void reload() {
        // Czyścimy mapę, żeby nie dublować przy przeładowaniu
        messages.clear();

        String lang = plugin.getConfig().getString("settings.language", "pl");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "languages/pl.yml");
        }

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Pobieramy sekcję "messages" z pliku YAML
        ConfigurationSection msgSection = langConfig.getConfigurationSection("messages");

        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                String msg = msgSection.getString(key);
                if (msg != null) {
                    messages.put(key, parseToLegacy(msg));
                }
            }
        } else {
            // Jeśli plik nie ma sekcji "messages:", czytamy wszystko z głównego poziomu
            for (String key : langConfig.getKeys(false)) {
                if (langConfig.isString(key)) {
                    messages.put(key, parseToLegacy(langConfig.getString(key)));
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

    /**
     * Główny parser: Zamienia tagi MiniMessage (gradienty, hexy) oraz stare kody '&'
     * na tradycyjny format kolorów (§), zwracany jako zwykły String.
     */
    private String parseToLegacy(String text) {
        if (text == null) return "";

        // 1. Jeśli linijka ma tagi MiniMessage (gradienty, hexy itp.)
        if (text.contains("<") && text.contains(">")) {
            try {
                // Podmieniamy ewentualne '&' na '§', żeby ujednolicić format przed parsowaniem
                String prepared = text.replace("&", "§");

                // MiniMessage bezpiecznie przetwarza tu gradienty i kolory HEX na Komponent
                Component parsed = miniMessage.deserialize(prepared);

                // Serializujemy komponent z powrotem do Stringa z gęsto rozsianymi znakami '§'
                // Dzięki temu silnik Minecrafta przeczyta gradient ze zwykłego Stringa!
                return LegacyComponentSerializer.legacySection().serialize(parsed);
            } catch (Exception e) {
                // Awaryjny ratunek w razie złej składni w pliku konfiguracyjnym
                return text.replace("&", "§");
            }
        }

        // 2. Jeśli linijka NIE MA tagów MiniMessage, traktujemy ją w 100% klasycznie
        return text.replace("&", "§");
    }

    // Pobiera czystą wiadomość z mapy (jako String)
    public String getMessage(String path) {
        return messages.getOrDefault(path, "§cMissing string: " + path);
    }

    // Pobiera wiadomość z prefixem (jako String)
    public String getWithPrefix(String path) {
        return parseToLegacy(AstraLogin.PREFIX) + " " + getMessage(path);
    }

    // Metoda z placeholderem (np. do {COUNT} lub %type%)
    public String getWithPrefix(String path, String placeholder, String value) {
        String msg = getMessage(path).replace(placeholder, value);
        return parseToLegacy(AstraLogin.PREFIX) + " " + msg;
    }
}