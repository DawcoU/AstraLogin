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

    // --- GETTERY (Dostęp dla innych klas) ---
    public LanguageManager getLanguageManager() { return languageManager; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public LoginSystem getLoginSystem() { return loginSystem; } // Kluczowe dla Twoich eventów!
    public void setLanguageManager(LanguageManager languageManager) { this.languageManager = languageManager; }
    public NoticeManager getNoticeManager() {
        return noticeManager;
    }

    @Override
    public void onEnable() {
        // --- 1. SYSTEM PLIKÓW I UPDATER ---
        saveDefaultConfig();

        FilesUpdater updater = new FilesUpdater(this);
        updater.check();

        // --- 2. INICJALIZACJA MANAGERÓW ---
        this.languageManager = new LanguageManager(this);
        this.inventoryStorage = new InventoryStorage(this);
        this.spawnManager = new SpawnManager(this);
        this.passwordManager = new PasswordManager(this);
        this.ipManager = new IPManager(this);
        this.noticeManager = new NoticeManager(this);

        // Tworzenie serca pluginu - LoginSystem
        this.loginSystem = new LoginSystem(this, this.passwordManager, this.inventoryStorage, this.ipManager, this.spawnManager);

        // --- 3. FILTRACJA LOGÓW (UKRYWANIE HASEŁ) ---
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

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        // Blokady świata (move, break, etc.)
        getServer().getPluginManager().registerEvents(new LoginBlocks(this, loginSystem, this.inventoryStorage, spawnManager), this);
        // Główne eventy logowania
        getServer().getPluginManager().registerEvents(loginSystem, this);

        // Komendy
        getCommand("zarejestruj").setExecutor(loginSystem);
        getCommand("zaloguj").setExecutor(loginSystem);
        getCommand("astralogin").setExecutor(loginSystem);
        getCommand("astralogin").setTabCompleter(loginSystem);
        getCommand("zresetujhaslo").setExecutor(loginSystem);
        getCommand("zmienhaslo").setExecutor(loginSystem);
        getCommand("zresetujip").setExecutor(new IPSecurity(this, ipManager));

        // --- 5. LOGO STARTOWE I SPRAWDZANIE WERSJI ---
        noticeManager.sendStartupLogo();

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
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
        noticeManager.sendShutdownLogo();
    }
}