package pl.dawcou.astralogin.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.util.*;

public class LanguageManager {

    private final JavaPlugin plugin;

    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Set<String> missingKeys = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles(); // Najpierw upewniamy się, że pliki są na dysku
        reload();
    }

    public void reload() {
        // Czyścimy mapę
        messages.clear();
        lists.clear();

        String lang = plugin.getConfig().getString("settings.language", "en");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "languages/en.yml");
        }

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Pobieramy sekcję "messages" z pliku YAML
        ConfigurationSection msgSection = langConfig.getConfigurationSection("messages");

        if (msgSection != null) {
            loadMessages(msgSection, "");
        }
    }

    public void printMissingKeys() {
        if (missingKeys.isEmpty()) {
            return;
        }

        plugin.getLogger().warning("==============================");
        plugin.getLogger().warning("Missing language keys:");

        for (String key : missingKeys) {
            plugin.getLogger().warning("- " + key);
        }

        plugin.getLogger().warning("==============================");
    }

    private void loadMessages(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty()
                    ? key
                    : path + "." + key;

            if (section.isConfigurationSection(key)) {
                loadMessages(
                        section.getConfigurationSection(key),
                        fullPath
                );
            } else if (section.isList(key)) {
                // Obsługa list w plikach językowych
                List<String> rawList = section.getStringList(key);
                List<String> parsedList = new ArrayList<>();

                for (String line : rawList) {
                    parsedList.add(parseToLegacy(line));
                }

                lists.put(fullPath, parsedList);
            } else {
                String message = section.getString(key);

                if (message != null) {
                    messages.put(
                            fullPath,
                            parseToLegacy(message)
                    );
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
    public String parseToLegacy(String text) {
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

    // Pobiera czystą wiadomość z mapy i od razu ją konwertuje
    public String getMessage(String path) {
        String rawMessage = messages.get(path);

        if (rawMessage == null) {
            missingKeys.add(path);
            return "§cMissing message: " + path;
        }

        return parseToLegacy(rawMessage);
    }

    public List<String> getMessageList(String path) {
        List<String> rawList = lists.get(path);

        if (rawList == null) {
            missingKeys.add(path);
            List<String> errorList = new ArrayList<>();
            errorList.add("§cMissing message list: " + path);
            return errorList;
        }

        return rawList;
    }

    // Pobiera wiadomość z prefixem
    public String getWithPrefix(String path) {
        return parseToLegacy(AstraLogin.PREFIX) + " " + getMessage(path);
    }
}