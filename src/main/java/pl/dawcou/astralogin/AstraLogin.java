package pl.dawcou.astralogin;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Główna klasa pluginu AstraLogin.
 * Zarządza startem, wyłączaniem i komunikacją między modułami.
 */
public class AstraLogin extends JavaPlugin implements Listener {

    // --- PREFIXY I STAŁE ---
    public static final String PREFIX = "<gradient:#0055FF:#33CCFF:#33CCFF:#33CCFF:#0055FF>[AstraLogin]</gradient>";
    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    // --- INSTANCJE MANAGERÓW (POLA) ---
    private static AstraLogin instance;
    private LanguageManager languageManager;
    private InventoryManager inventoryManager;
    private LoginSystem loginSystem;
    private LoginListeners loginListeners;
    private SpawnManager spawnManager;
    private PasswordManager passwordManager;
    private IPManager ipManager;
    private NoticeManager noticeManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private FilesConverter filesConverter;
    private LogManager logManager;
    private AccountDataManager accountDataManager;

    // --- GETTERY (Dostęp dla innych klas) ---
    public LanguageManager getLanguageManager() { return languageManager; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public LoginSystem getLoginSystem() { return loginSystem; }
    public void setLanguageManager(LanguageManager languageManager) { this.languageManager = languageManager; }
    public IPManager getIPManager() { return ipManager; }
    public NoticeManager getNoticeManager() {
        return noticeManager;
    }
    public InventoryManager getInventoryManager() { return this.inventoryManager; }
    public SpawnManager getSpawnManager() { return this.spawnManager; }
    public SessionManager getSessionManager() { return this.sessionManager; }
    public AttemptManager getAttemptManager() { return this.attemptManager; }
    public FilesConverter getFilesConverter() { return this.filesConverter; }
    public LogManager getLogManager() { return this.logManager; }
    public AccountDataManager getAccountDataManager() { return this.accountDataManager; }
    public static AstraLogin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // 1. Pliki na dysk
        instance = this;
        saveDefaultConfig();

        int pluginId = 31501;
        new Metrics(this, pluginId);

        IPSecurity.ipCheckOctets = this.getConfig().getInt("security.ip-security.ip-check-octets", 4);

        // 2. Migracje danych (Muszą wykonać się przed jakimkolwiek odczytem przez managery)
        new FilesConverter(this).runAllMigrations();

        this.noticeManager = new NoticeManager(this);

        FilesUpdater updater = new FilesUpdater(this);
        updater.check();

        // 3. Infrastruktura diagnostyczna i językowa
        this.logManager = new LogManager(this);
        this.languageManager = new LanguageManager(this);
        this.languageManager.reload(); // Ładujemy języki od razu, aby komunikaty były dostępne

        // 4. Inicjalizacja managerów logicznych
        this.accountDataManager = new AccountDataManager(this);
        this.inventoryManager = new InventoryManager(this);
        this.spawnManager = new SpawnManager(this);
        this.passwordManager = new PasswordManager(this);
        this.ipManager = new IPManager(this);
        this.sessionManager = new SessionManager(this);
        this.attemptManager = new AttemptManager(this);
        this.loginListeners = new LoginListeners(this);

        // 5. Wczytywanie baz danych i cache
        this.passwordManager.reload();
        this.ipManager.reload();

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

        // Tworzenie serca pluginu - LoginSystem
        this.loginSystem = new LoginSystem(this, this.passwordManager, this.inventoryManager, this.ipManager, this.spawnManager);

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        // Ten zajmuje się Join, Quit i PreLogin
        getServer().getPluginManager().registerEvents(this.loginListeners, this);

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
        getCommand("konto").setExecutor(new AccountManager(this));
        getCommand("zresetujkonto").setExecutor(new AccountManager(this));
        getCommand("listaip").setExecutor(new AccountManager(this));

        this.sessionManager.loadSessionsFromConfig();

        // Zapisuj sesje co 10 minut (asynchronicznie, żeby nie lagować głównego wątku)
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (this.sessionManager != null) {
                this.sessionManager.saveSessionsToConfig();
            }
        }, 10, 10, java.util.concurrent.TimeUnit.MINUTES);

        // --- 5. LOGO STARTOWE I SPRAWDZANIE WERSJI ---
        // Odpalamy scheduler asynchroniczny, który najpierw sprawdzi internet, a na koniec wypluje logo i status wersji!
        this.getServer().getAsyncScheduler().runNow(this, task -> {

            // Najpierw sprawdzamy aktualizacje, jeśli opcja jest włączona
            if (getConfig().getBoolean("settings.check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getDescription().getVersion();

                    // 1. NAJPIERW DRUKUJEMY LOGO (Zawsze jako pierwsze, niezależnie od wyniku sieci)
                    noticeManager.sendStartupLogo();

                    // 2. ZARAZ POD LOGO DORZUCAMY INFO O WERSJI
                    if (currentVersion.equals(version)) {
                        noticeManager.sendVersionOk(version);
                    } else if (currentVersion.compareTo(version) > 0) {
                        noticeManager.sendDevNotice(currentVersion, version);
                    } else {
                        noticeManager.sendUpdateNotice(Bukkit.getConsoleSender(), version);
                    }
                });
            } else {
                // Jeśli admin wyłączył sprawdzanie aktualizacji, po prostu drukujemy samo logo!
                noticeManager.sendStartupLogo();
            }
        });
    }

    @Override
    public void onDisable() {
        // DEBUG: Sprawdźmy co żyje
        if (this.loginListeners == null) {
            getLogger().severe("UWAGA: loginListeners jest NULL w onDisable! Szukaj błędu w onEnable!");
            return;
        }


        for (Player p : Bukkit.getOnlinePlayers()) {
            this.loginListeners.handleQuit(p);
        }
        noticeManager.sendShutdownLogo();
    }
}