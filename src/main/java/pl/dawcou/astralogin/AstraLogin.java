package pl.dawcou.astralogin;

import org.bstats.bukkit.Metrics;
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
    private InventoryManager inventoryManager;
    private LoginSystem loginSystem;
    private SpawnManager spawnManager;
    private PasswordManager passwordManager;
    private IPManager ipManager;
    private NoticeManager noticeManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private FilesConverter filesConverter;
    private LogManager logManager;

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
    public FilesConverter getFilesConverter() { return this.filesConverter; }
    public LogManager getLogManager() { return this.logManager; }

    @Override
    public void onEnable() {
        // 1. Pliki na dysk
        saveDefaultConfig();

        int pluginId = 31501;
        new Metrics(this, pluginId);

        // Ładujemy zakres sprawdzania IP z configu prosto do klasy IPSecurity przy starcie serwera
        IPSecurity.ipCheckOctets = this.getConfig().getInt("security.ip-security.ip-check-octets", 4);

        // 2. Migracje na plikach (Dysk)
        new FilesConverter(this).runAllMigrations();

        // 3. Odpalamy managery (One tworzą puste szablony lub czytają pliki)
        this.noticeManager = new NoticeManager(this);
        this.languageManager = new LanguageManager(this);
        this.inventoryManager = new InventoryManager(this);
        this.spawnManager = new SpawnManager(this);
        this.passwordManager = new PasswordManager(this);
        this.ipManager = new IPManager(this);
        this.sessionManager = new SessionManager(this);
        this.attemptManager = new AttemptManager(this);

        this.passwordManager.reload();
        this.ipManager.reload();
        this.logManager = new LogManager(this);

        // 4. Aktualizacje i przeładowanie języków
        FilesUpdater updater = new FilesUpdater(this);
        updater.check();
        this.languageManager.reload();

        // Tworzenie serca pluginu - LoginSystem
        this.loginSystem = new LoginSystem(this, this.passwordManager, this.inventoryManager, this.ipManager, this.spawnManager);

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
        // Ten zajmuje się Join, Quit i PreLogin
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

        // Zapisuj sesje co 10 minut (asynchronicznie, żeby nie lagować głównego wątku)
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (this.sessionManager != null) {
                this.sessionManager.saveSessionsToConfig();
                // Opcjonalnie: getLogger().info("Automatycznie zapisano sesje AstraLogin.");
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
        // --- RATOWANIE DANYCH GRACZY PRZED WYŁĄCZENIEM ---
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();

            if (loginSystem.getZalogowani().contains(uuid)) {
                spawnManager.saveLastLocation(p);

                if (getConfig().getBoolean("features.session.session-enabled")) {
                    loginSystem.getSesje().put(uuid, System.currentTimeMillis());
                    loginSystem.getSesjeIP().put(uuid, p.getAddress().getAddress().getHostAddress());
                }

            } else {
                // Jeśli nie był zalogowany, oddajemy mu itemy, żeby nie "zniknęły"
                inventoryManager.restore(p);
            }
        }

        // Teraz wywołujemy zapis – mapa w RAM-ie jest już pełna graczy online!
        if (this.sessionManager != null) {
            this.sessionManager.saveSessionsToConfig();
        }

        noticeManager.sendShutdownLogo();
    }
}