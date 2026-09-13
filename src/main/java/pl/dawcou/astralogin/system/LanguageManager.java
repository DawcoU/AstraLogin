package pl.dawcou.astralogin.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LanguageManager {

    private final JavaPlugin plugin;
    private String currentLang;

    // Przechowujemy SUROWE wiadomości z YML (nie sparsowane!)
    private final Map<String, String> rawMessages = new HashMap<>();
    private final Map<String, List<String>> rawLists = new HashMap<>();
    private final Set<String> missingKeys = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat() // Kodowanie hexów dla klasycznego Bukkita (§x§f...)
            .build();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles();
    }

    public String getLang() {
        return currentLang;
    }

    public void reload() {
        rawMessages.clear();
        rawLists.clear();
        missingKeys.clear();

        String lang = plugin.getConfig().getString("settings.language", "en");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            lang = "en";
            langFile = new File(plugin.getDataFolder(), "languages/en.yml");
        }

        currentLang = lang;

        if (langFile.exists()) {
            FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            loadMessages(langConfig, "");
        } else {
            // Zabezpieczenie: jeśli na dysku nie ma nawet en.yml, wczytaj bootstrapowo z JARa
            bootstrap();
        }
    }

    public void bootstrap() {
        rawMessages.clear();
        rawLists.clear();

        String lang = plugin.getConfig().getString("settings.language", "en");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        // 1. Jeśli plik istnieje na dysku -> ładujemy go
        if (langFile.exists()) {
            currentLang = lang;
            FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            loadMessages(langConfig, "");
            return;
        }

        // 2. Jeśli plik NIE istnieje na dysku (pierwsze odpalenie) -> czytamy z wnętrza pliku JAR
        InputStream internalStream = plugin.getResource("languages/" + lang + ".yml");
        if (internalStream == null) {
            // Fallback do angielskiego z JARa
            internalStream = plugin.getResource("languages/en.yml");
            currentLang = "en";
        } else {
            currentLang = lang;
        }

        if (internalStream != null) {
            try (InputStreamReader reader = new InputStreamReader(internalStream, StandardCharsets.UTF_8)) {
                FileConfiguration langConfig = YamlConfiguration.loadConfiguration(reader);
                loadMessages(langConfig, "");
            } catch (Exception e) {
                plugin.getLogger().severe("Could not load bootstrap language resource: " + e.getMessage());
            }
        }
    }

    public void printMissingKeys() {
        if (((AstraLogin) plugin).isDebugEnabled()) return;
        if (missingKeys.isEmpty()) return;

        plugin.getLogger().warning("==============================");
        plugin.getLogger().warning("Missing language keys:");
        for (String key : missingKeys) {
            plugin.getLogger().warning("- " + key);
        }
        plugin.getLogger().warning("==============================");
    }

    private void loadMessages(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (section.isConfigurationSection(key)) {
                loadMessages(section.getConfigurationSection(key), fullPath);
            } else if (section.isList(key)) {
                // Zapisujemy surową listę bez ponownego parsowania na tym etapie
                rawLists.put(fullPath, section.getStringList(key));
            } else {
                String message = section.getString(key);
                if (message != null) {
                    rawMessages.put(fullPath, message);
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

    public String parseToLegacy(String text) {
        if (text == null) return "";

        String result = text;

        // 1. Jeśli linijka ma tagi MiniMessage (gradienty, hexy itp.)
        if (result.contains("<") && result.contains(">")) {
            try {
                Component parsed = miniMessage.deserialize(result);
                // Używamy naszego parsera z obsługą HEX!
                result = legacySerializer.serialize(parsed);
            } catch (Exception ignored) {}
        }

        // 2. Na końcu zamieniamy stare kody & na §
        return result.replace("&", "§");
    }

    public String getRawMessage(String path) {
        return findValue(rawMessages, path);
    }

    public String getMessage(String path) {
        String raw = getRawMessage(path);

        if (raw == null) {
            missingKeys.add(path);
            return "§cMissing message: " + path;
        }

        return parseToLegacy(raw);
    }

    public List<String> getMessageList(String path) {
        List<String> rawList = findValue(rawLists, path);

        if (rawList == null) {
            missingKeys.add(path);
            List<String> errorList = new ArrayList<>();
            errorList.add("§cMissing message list: " + path);
            return errorList;
        }

        List<String> parsedList = new ArrayList<>();
        for (String line : rawList) {
            parsedList.add(parseToLegacy(line));
        }
        return parsedList;
    }

    private <T> T findValue(Map<String, T> map, String path) {
        if (map.containsKey(path)) {
            return map.get(path);
        }

        if (path.startsWith("messages.")) {
            String subPath = path.substring(9);
            if (map.containsKey(subPath)) {
                return map.get(subPath);
            }
        } else {
            String prefixedPath = "messages." + path;
            if (map.containsKey(prefixedPath)) {
                return map.get(prefixedPath);
            }
        }

        return null;
    }

    // -------------------------------------------------------------
    // Pobiera wiadomość z prefixem dostosowanym do typu odbiorcy
    // -------------------------------------------------------------
    public String getWithPrefix(CommandSender receiver, String path) {
        String rawMessage = getRawMessage(path);

        if (rawMessage == null) {
            missingKeys.add(path);
            String prefix = (receiver instanceof ConsoleCommandSender) ? AstraLogin.PREFIX2 : AstraLogin.PREFIX;
            return parseToLegacy(prefix) + " §cMissing message: " + path;
        }

        // Konsola dostaje prosty PREFIX2 bez HEX-ów, gracz dostaje gradient PREFIX
        if (receiver instanceof ConsoleCommandSender) {
            return parseToLegacy(AstraLogin.PREFIX2 + " " + rawMessage);
        } else {
            return parseToLegacy(AstraLogin.PREFIX + " " + rawMessage);
        }
    }

    // Wersja domyślna (dla graczy)
    public String getWithPrefix(String path) {
        String rawMessage = getRawMessage(path);

        if (rawMessage == null) {
            missingKeys.add(path);
            return parseToLegacy(AstraLogin.PREFIX) + " §cMissing message: " + path;
        }

        return parseToLegacy(AstraLogin.PREFIX + " " + rawMessage);
    }
}