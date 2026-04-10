package pl.dawcou.astralogin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration langConfig;
    private final Map<String, String> messageCache = new HashMap<>();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles();
        loadLanguage();
    }

    // Tworzy folder languages i domyślne pliki, jeśli ich nie ma
    private void setupFiles() {
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Lista plików do wypakowania z resources
        String[] defaultLangs = {"pl.yml", "en.yml"};
        for (String langFile : defaultLangs) {
            File file = new File(langFolder, langFile);
            if (!file.exists()) {
                plugin.saveResource("languages/" + langFile, false);
            }
        }
    }

    // Ładuje wybrany język z config.yml
    public void loadLanguage() {
        String lang = plugin.getConfig().getString("language", "pl");
        File file = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!file.exists()) {
            // Pobieramy język z configu, żeby wiedzieć, co wywalić w logach
            String currentLang = plugin.getConfig().getString("language", "pl");

            // Jeśli config mówi po polsku, dajemy polskie ostrzeżenie, w przeciwnym razie angielskie
            if (currentLang.equalsIgnoreCase("pl")) {
                plugin.getLogger().warning("§cPlik językowy '" + currentLang + ".yml' nie istnieje! §6Ładowanie domyślnego pliku pl.yml");
            } else {
                plugin.getLogger().warning("§cLanguage file '" + currentLang + ".yml' does not exist! §6Loading default file pl.yml");
            }

            // Ustawiamy plik na domyślny pl.yml
            file = new File(plugin.getDataFolder(), "languages/pl.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(file);

        // Czyścimy cache przy przeładowaniu
        messageCache.clear();
    }

    // Główna metoda do pobierania wiadomości
    public String getMessage(String path) {
        // 1. Sprawdzamy czy już to mamy w pamięci
        if (messageCache.containsKey(path)) {
            return messageCache.get(path);
        }

        // 2. Pobieramy z pliku
        String rawMessage = langConfig.getString("messages." + path);

        if (rawMessage == null) {
            return "§cNo message found: " + path;
        }

        // 3. Formatuje kolory
        String formatted = ChatColor.translateAlternateColorCodes('&', rawMessage);

        // 4. KLUCZOWE: Zapisujemy do cache, żeby przy następnym razu nie szukać w pliku!
        messageCache.put(path, formatted);

        return formatted;
    }

    public String getWithoutPrefix(String path) {
        return getMessage(path);
    }

    // Metoda, która automatycznie dokleja PREFIX z głównej klasy
    public String getWithPrefix(String path) {
        return AstraLogin.PREFIX + " " + getMessage(path);
    }

    // Metoda dla jednego placeholdera (najczęstszy przypadek)
    public String getWithPrefix(String path, String placeholder, String value) {
        String msg = getMessage(path).replace(placeholder, value);
        return AstraLogin.PREFIX + " " + msg;
    }
}