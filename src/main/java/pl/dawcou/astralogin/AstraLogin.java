package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

import java.util.UUID;

public class AstraLogin extends JavaPlugin implements Listener {

    public static final String PREFIX = ChatColor.of("#0088FF") + "[" + ChatColor.of("#00D5FF") + "AstraLogin" + ChatColor.of("#0088FF") + "]";

    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    private LanguageManager languageManager;
    private InventoryStorage inventoryStorage;
    private LoginSystem loginSystem;
    private SpawnManager spawnManager;
    private InventoryStorage storage;

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public void onEnable() {
        // --- 1. INICJALIZACJA (TYLKO RAZ!) ---
        this.languageManager = new LanguageManager(this);
        this.inventoryStorage = new InventoryStorage(this);
        this.storage = this.inventoryStorage;

        PasswordManager passwordManager = new PasswordManager(this);
        IPManager ipManager = new IPManager(this);
        this.spawnManager = new SpawnManager(this);

        // Budujemy loginSystem
        this.loginSystem = new LoginSystem(this, passwordManager, this.inventoryStorage, ipManager, spawnManager);

        // --- 2. LOGGER ---
        try {
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addFilter(new LogFilter(this));
            getLogger().info(ChatColor.stripColor(languageManager.getMessage("logger-success")));
        } catch (Exception e) {
            if (languageManager != null) {
                String errorMsg = languageManager.getMessage("logger-error").replace("%error%", e.getMessage());
                getLogger().severe(ChatColor.stripColor(errorMsg));
            }
        }

        // --- 3. CONFIG I FILES ---
        saveDefaultConfig();
        new FilesUpdater(this).check();

        getServer().getPluginManager().registerEvents(new LoginBlocks(this, loginSystem, this.inventoryStorage, spawnManager), this);

        // --- 4. REJESTRACJA KOMEND I EVENTÓW ---
        getCommand("zarejestruj").setExecutor(loginSystem);
        getCommand("zaloguj").setExecutor(loginSystem);
        getCommand("astralogin").setExecutor(loginSystem);
        getCommand("astralogin").setTabCompleter(loginSystem);
        getCommand("zresetujhaslo").setExecutor(loginSystem);
        getCommand("zmienhaslo").setExecutor(loginSystem);
        getCommand("zresetujip").setExecutor(new IPSecurity(this, ipManager));
        getServer().getPluginManager().registerEvents(loginSystem, this);

        // --- 5. LOGI STARTOWE WBUDOWANE ---
        sendStartupLogo();

        // --- 6. SPRAWDZANIE NOWEJ WERSJI:
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (getConfig().getBoolean("check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getDescription().getVersion();

                    if (currentVersion.equals(version)) {
                        getLogger().info("");
                        sendVersionOk(version);
                        getLogger().info("");
                    }
                    // Sprawdzamy, czy masz wersję wyższą niż na Modrinth (np. Twoje 2.2.0 vs 2.1.0 na stronie)
                    else if (currentVersion.compareTo(version) > 0) {
                        sendDevNotice(currentVersion, version);
                    }
                    // Standardowa informacja o aktualizacji (Twój plugin jest starszy)
                    else {
                        sendUpdateNotice(version);
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {
        // 1. Przechodzimy przez wszystkich graczy online w momencie wyłączenia
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();

            // 2. Jeśli był zalogowany, ratujemy jego pozycję!
            if (loginSystem.getZalogowani().contains(uuid)) {
                spawnManager.saveLastLocation(p);
            }

            // 3. Jeśli nie był zalogowany, oddajemy mu itemy, żeby nie zniknęły po usunięciu pliku
            if (!loginSystem.getZalogowani().contains(uuid)) {
                storage.restore(p);
            }
        }

        // 2. Odpalamy Logi Wyłączania
        sendShutdownLogo();
    }

    // Metody Językowe dla innych klas

    public void sendConfigUpdateNotice() {
        String lang = getConfig().getString("language", "pl");

        String msg = lang.equalsIgnoreCase("pl")
                ? "§aPomyślnie dopisano brakujące linijki do configu"
                : "§aSuccessfully added missing lines to the config";

        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String lang = getConfig().getString("language", "pl");

        String msg = lang.equalsIgnoreCase("pl")
                ? "§cBłąd podczas zapisu configu: "
                : "§cError while saving config: ";

        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + error);
    }

    public void sendInvalidSessionFormatNotice() {
        String lang = getConfig().getString("language", "pl");
        String msg = lang.equalsIgnoreCase("pl")
                ? "§cNieprawidłowy format session-time! Używam domyślnych 5 minut."
                : "§cInvalid session-time format! Using default 5 minutes.";
        getLogger().warning(msg);
    }

    // 1. Błąd tworzenia pliku lokalizacji
    public void sendSpawnCreateError() {
        String lang = getConfig().getString("language", "pl");
        String msg = lang.equalsIgnoreCase("pl")
                ? "§cNie można utworzyć pliku spawns/locations.yml!"
                : "§cCould not create spawns/locations.yml file!";
        getLogger().severe(msg);
    }

    // 2. Błąd odczytu pozycji gracza
    public void sendPlayerLocationReadError(String playerName) {
        String lang = getConfig().getString("language", "pl");
        String msg = lang.equalsIgnoreCase("pl")
                ? "Błąd podczas odczytu pozycji dla " + playerName
                : "Error while reading location for " + playerName;
        getLogger().warning(msg);
    }

    // 3. Błąd zapisu pliku lokalizacji
    public void sendSpawnSaveError() {
        String lang = getConfig().getString("language", "pl");
        String msg = lang.equalsIgnoreCase("pl")
                ? "§cNie można zapisać pliku spawns/locations.yml!"
                : "§cCould not save spawns/locations.yml file!";
        getLogger().severe(msg);
    }

    // 4. Błąd sprawdzania aktualizacji
    public void sendUpdateCheckError() {
        String lang = getConfig().getString("language", "pl");
        String msg = lang.equalsIgnoreCase("pl")
                ? "§cNie udało się sprawdzić aktualizacji na Modrinth"
                : "§cFailed to check for updates on Modrinth";
        getLogger().warning(msg);
    }

    public void sendNoIPSaved(CommandSender sender) {
        // Sprawdzamy język z configu (domyślnie polski)
        String lang = getConfig().getString("language", "pl");

        // Wybieramy odpowiednią wersję tekstu
        String msg = lang.equalsIgnoreCase("pl")
                ? "§cTen gracz nie ma zapisanego adresu IP!"
                : "§cThis player does not have a saved IP address!";

        // Wysyłamy z prefixem
        sender.sendMessage(PREFIX + " " + msg);
    }

    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    private void sendVersionOk(String version) {
        String lang = getConfig().getString("language", "pl");

        // Szybkie tłumaczenie w jednej linii
        String msg = lang.equalsIgnoreCase("pl")
                ? "§aAstraLogin jest aktualny §f(§ev" + version + "§f)"
                : "§aAstraLogin is up to date §f(§ev" + version + "§f)";

        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendUpdateNotice(String version) {
        String lang = getConfig().getString("language", "pl");

        // Tłumaczenia
        String title = lang.equalsIgnoreCase("pl") ? "§eDostępna jest nowa wersja AstraLogin: §fv" : "§eA new version of AstraLogin is available: §fv";
        String download = lang.equalsIgnoreCase("pl") ? "§aPobierz: " : "§aDownload: ";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(title + version);
        Bukkit.getConsoleSender().sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void sendDevNotice(String currentVersion, String latestStable) {
        String lang = getConfig().getString("language", "pl");

        // Tłumaczenia
        String devTitle = lang.equalsIgnoreCase("pl") ? "§bUżywasz wersji testowej (Development): §f§nv" : "§bYou are using a Development version: §f§nv";
        String stableInfo = lang.equalsIgnoreCase("pl") ? "§eNa Modrinth najnowsza stabilna to: §fv" : "§eThe latest stable on Modrinth is: §fv";
        String warning = lang.equalsIgnoreCase("pl") ? "§bUważaj na błędy, kod jest w fazie rozwoju!" : "§bWatch out for bugs, the code is in development!";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(devTitle + currentVersion);
        Bukkit.getConsoleSender().sendMessage(stableInfo + latestStable);
        Bukkit.getConsoleSender().sendMessage(warning);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void sendStartupLogo() {
        String v = getDescription().getVersion();
        // Pobieramy język bezpośrednio z configu, żeby wiedzieć co wyświetlić
        String lang = getConfig().getString("language", "pl");

        // Tłumaczenia "na sztywno" w kodzie
        String status = lang.equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = lang.equalsIgnoreCase("pl") ? "§6   Autor: §e" : "§6   Author: §e";
        String statusLabel = lang.equalsIgnoreCase("pl") ? "§6   Status: " : "§6   Status: ";

        // Wyświetlanie w konsoli
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   AstraLogin §ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void sendShutdownLogo() {
        // Pobieramy język z configu
        String lang = getConfig().getString("language", "pl");

        // Tłumaczenia statusu i pożegnania
        String status = lang.equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = lang.equalsIgnoreCase("pl") ? "§eDo zobaczenia! :D" : "§eSee you! :D";

        // Wyświetlanie w konsoli
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ §4[§cAstraLogin§4] §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }
}