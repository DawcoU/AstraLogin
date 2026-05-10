package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;
import java.util.UUID;

/**
 * Główna klasa pluginu AstraLogin.
 * Zarządza startem, wyłączaniem i komunikacją między modułami.
 */
public class AstraLogin extends JavaPlugin implements Listener {

    // --- PREFIXY I STAŁE ---
    public static final String PREFIX = ChatColor.of("#0088FF") + "[" + ChatColor.of("#00D5FF") + "AstraLogin" + ChatColor.of("#0088FF") + "]";
    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    // --- INSTANCJE MANAGERÓW (POLA) ---
    private LanguageManager languageManager;
    private InventoryStorage inventoryStorage;
    private LoginSystem loginSystem;
    private SpawnManager spawnManager;
    private PasswordManager passwordManager;
    private IPManager ipManager;
    private NoticeManager noticeManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;

    // --- GETTERY (Dostęp dla innych klas) ---
    public LanguageManager getLanguageManager() { return languageManager; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public LoginSystem getLoginSystem() { return loginSystem; }
    public void setLanguageManager(LanguageManager languageManager) { this.languageManager = languageManager; }
    public IPManager getIPManager() { return ipManager; }
    public NoticeManager getNoticeManager() {
        return noticeManager;
    }
    public SpawnManager getSpawnManager() { return this.spawnManager; }
    public SessionManager getSessionManager() { return this.sessionManager; }
    public AttemptManager getAttemptManager() { return this.attemptManager; }

    @Override
    public void onEnable() {
        // --- 1. NAJPIERW WSZYSTKIE MANAGERY (Narzędzia) ---
        this.noticeManager = new NoticeManager(this);
        this.languageManager = new LanguageManager(this);
        this.inventoryStorage = new InventoryStorage(this);
        this.spawnManager = new SpawnManager(this);
        this.passwordManager = new PasswordManager(this);
        this.ipManager = new IPManager(this);
        this.sessionManager = new SessionManager(this);
        this.attemptManager = new AttemptManager(this);

        // --- 2. POTEM OPERACJE NA PLIKACH I LOGIKA ---
        saveDefaultConfig();

        FilesUpdater updater = new FilesUpdater(this);
        updater.check();

        this.languageManager.reload();

        // Tworzenie serca pluginu - LoginSystem
        this.loginSystem = new LoginSystem(this, this.passwordManager, this.inventoryStorage, this.ipManager, this.spawnManager);

        // --- 3. FILTRACJA LOGÓW (UKRYWANIE HASEŁ) ---
        try {
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addFilter(new LogFilter(this));

            // Sukces
            noticeManager.sendLoggerSuccess();

        } catch (Exception e) {
            // Używamy nowej metody do obsługi błędu loggera
            noticeManager.sendLoggerError(e);
        }

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        // Ten zajmuje się Join, Quit i PreLogin (Twoje sesje tam są)
        getServer().getPluginManager().registerEvents(new LoginListeners(this), this);

        // Ten zajmuje się blokowaniem niszczenia bloków, ruchu itp. dla niezalogowanych
        getServer().getPluginManager().registerEvents(new LoginBlocks(this), this);

        // Komendy
        getCommand("zarejestruj").setExecutor(loginSystem);
        getCommand("zaloguj").setExecutor(loginSystem);
        getCommand("astralogin").setExecutor(loginSystem);
        getCommand("astralogin").setTabCompleter(loginSystem);
        getCommand("zresetujhaslo").setExecutor(loginSystem);
        getCommand("zmienhaslo").setExecutor(loginSystem);
        getCommand("zresetujip").setExecutor(new IPSecurity(this, ipManager));

        this.sessionManager.loadSessionsFromConfig();

        // --- 5. LOGO STARTOWE I SPRAWDZANIE WERSJI ---
        noticeManager.sendStartupLogo();

        // Zapisuj sesje co 10 minut (asynchronicznie, żeby nie lagować głównego wątku)
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (this.sessionManager != null) {
                this.sessionManager.saveSessionsToConfig();
                // Opcjonalnie: getLogger().info("Automatycznie zapisano sesje AstraLogin.");
            }
        }, 10, 10, java.util.concurrent.TimeUnit.MINUTES);

        this.getServer().getAsyncScheduler().runNow(this, task -> {
            if (getConfig().getBoolean("check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getDescription().getVersion();
                    if (currentVersion.equals(version)) {
                        noticeManager.sendVersionOk(version);
                    } else if (currentVersion.compareTo(version) > 0) {
                        noticeManager.sendDevNotice(currentVersion, version);
                    } else {
                        noticeManager.sendUpdateNotice(Bukkit.getConsoleSender(), version);
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {
        // --- RATOWANIE DANYCH GRACZY PRZED WYŁĄCZENIEM ---
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();

            if (loginSystem.getZalogowani().contains(uuid)) {
                spawnManager.saveLastLocation(p);
            } else {
                // Jeśli nie był zalogowany, oddajemy mu itemy, żeby nie "zniknęły"
                inventoryStorage.restore(p);
            }
        }

        if (this.sessionManager != null) {
            this.sessionManager.saveSessionsToConfig();
        }

        noticeManager.sendShutdownLogo();
    }
}