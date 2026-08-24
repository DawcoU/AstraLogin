package pl.dawcou.astralogin.auth;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import pl.dawcou.astralogin.auth.manage.InventoryManager;
import pl.dawcou.astralogin.accounts.AccountManager;
import pl.dawcou.astralogin.accounts.AccountCommand;
import pl.dawcou.astralogin.auth.security.passwords.PINManager;
import pl.dawcou.astralogin.auth.security.passwords.PasswordManager;
import pl.dawcou.astralogin.auth.security.premium.PremiumManager;
import pl.dawcou.astralogin.auth.security.premium.listener.PremiumProtocolListener;
import pl.dawcou.astralogin.auth.security.IPTrustManager;
import pl.dawcou.astralogin.file.BackupManager;
import pl.dawcou.astralogin.file.FilesUpdater;
import pl.dawcou.astralogin.file.converters.MigrationManager;
import pl.dawcou.astralogin.logging.LogFilter;
import pl.dawcou.astralogin.logging.LogManager;
import pl.dawcou.astralogin.auth.manage.LoginBlocks;
import pl.dawcou.astralogin.auth.manage.LoginListeners;
import pl.dawcou.astralogin.auth.manage.SpawnManager;
import pl.dawcou.astralogin.auth.security.AttemptManager;
import pl.dawcou.astralogin.auth.security.IPManager;
import pl.dawcou.astralogin.system.LanguageManager;
import pl.dawcou.astralogin.system.NoticeManager;
import pl.dawcou.astralogin.system.SchedulerManager;
import pl.dawcou.astralogin.system.UpdateChecker;
import pl.dawcou.astralogin.auth.twofactor.TwoFactorCommand;
import pl.dawcou.astralogin.auth.twofactor.TwoFactorManager;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AstraLogin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- PREFIXY I STAŁE ---
    public static final String PREFIX = "<gradient:#0055FF:#33CCFF:#33CCFF:#33CCFF:#0055FF>[AstraLogin]</gradient>";
    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    private static AstraLogin instance;
    private BukkitAudiences adventure;

    public boolean debugMode;

    private KeyPair keyPair;

    // --- INSTANCJE MANAGERÓW (POLA) ---
    private SchedulerManager schedulerManager;
    private LanguageManager languageManager;
    private InventoryManager inventoryManager;
    private LoginSystem loginSystem;
    private ProtocolManager protocolManager;
    private PremiumProtocolListener premiumProtocolListener;
    private FilesUpdater filesUpdater;
    private UpdateChecker updateChecker;
    private LoginListeners loginListeners;
    private PremiumManager premiumManager;
    private IPTrustManager ipTrustManager;
    private SpawnManager spawnManager;
    private PasswordManager passwordManager;
    private PINManager pinManager;
    private IPManager ipManager;
    private NoticeManager noticeManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private LogManager logManager;
    private AccountManager accountManager;
    private BackupManager backupManager;
    private TwoFactorManager twoFactorManager;

    // --- GETTERY (Dostęp dla innych klas) ---
    public SchedulerManager getSchedulerManager() { return schedulerManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public PINManager getPinManager() { return pinManager; }
    public LoginSystem getLoginSystem() { return loginSystem; }
    public PremiumManager getPremiumManager() { return premiumManager; }
    public IPManager getIPManager() { return ipManager; }
    public IPTrustManager getIpTrustManager() { return ipTrustManager; }
    public NoticeManager getNoticeManager() {return noticeManager; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public SpawnManager getSpawnManager() { return spawnManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public LogManager getLogManager() { return logManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public AccountManager getAccountDataManager() { return accountManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }
    public ProtocolManager getProtocolManager() { return protocolManager; }
    public PremiumProtocolListener getPremiumProtocolListener() { return premiumProtocolListener; }

    public static AstraLogin getInstance() { return instance; }

    public BukkitAudiences getAdventure() { return this.adventure; }

    public KeyPair getKeyPair() { return keyPair; }

    public boolean isDebugMode() { return debugMode; }

    private void reload() {
        debugMode = getConfig().getBoolean("settings.debug-mode", false);
    }

    @Override
    public void onEnable() {
        if (Bukkit.getOnlineMode()) {
            getLogger().warning("Server is running in online-mode. Premium Login has been disabled because Mojang already verifies player accounts");
        }

        int pluginId = 31501;
        new Metrics(this, pluginId);

        instance = this;
        this.adventure = BukkitAudiences.create(this);

        // 1. Pliki na dysk
        saveDefaultConfig();
        debugMode = getConfig().getBoolean("settings.debug-mode", false);

        // Tworzenie serca pluginu - LoginSystem i SchedulerManager
        schedulerManager = new SchedulerManager(this);
        loginSystem = new LoginSystem(this);

        // 2. Migracje danych (Muszą wykonać się przed jakimkolwiek odczytem przez managery)
        new MigrationManager(this).migrate();

        // ↓ ↓ ↓ ↓ ↓
        // Tak migratorze masz rację powinienem zostać przesunięty pod tobą XDDD
        IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 4);

        languageManager = new LanguageManager(this);
        languageManager.reload(); // Ładujemy języki od razu, aby komunikaty były dostępne

        noticeManager = new NoticeManager(this);

        filesUpdater = new FilesUpdater(this);
        filesUpdater.check();

        // 3. Infrastruktura diagnostyczna i językowa
        logManager = new LogManager(this);
        updateChecker = new UpdateChecker(this);

        // 4. Inicjalizacja managerów logicznych
        twoFactorManager = new TwoFactorManager(this);
        accountManager = new AccountManager(this);
        inventoryManager = new InventoryManager(this);
        spawnManager = new SpawnManager(this);
        passwordManager = new PasswordManager(this);
        pinManager = new PINManager(this);
        ipManager = new IPManager(this);
        ipTrustManager = new IPTrustManager(this);
        sessionManager = new SessionManager(this);
        attemptManager = new AttemptManager(this);
        premiumManager = new PremiumManager(this);

        try {
            // Generujemy parę kluczy RSA (1024-bit) dla autoryzacji Premium
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            getLogger().severe("Failed to generate RSA keys: " + e.getMessage());
        }

        // 3. Sprawdzenie i podpięcie ProtocolLiba oraz listenera autologowania
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            // TWORZYMY LISTENER PROTOKOŁU TYLKO RAZ!
            premiumProtocolListener = new PremiumProtocolListener(this, protocolManager);
        } else {
            getLogger().warning("ProtocolLib is missing from the server! Premium autologin will be disabled.");
        }

        loginListeners = new LoginListeners(this, premiumProtocolListener);
        backupManager = new BackupManager(this);
        AccountCommand accountCommand = new AccountCommand(this);
        TwoFactorCommand twoFactorCommand = new TwoFactorCommand(this, twoFactorManager, loginSystem);

        // 5. Wczytywanie baz danych i cache
        passwordManager.reload();
        pinManager.reload();

        ipManager.reload();

        // --- 3. FILTRACJA LOGÓW (UKRYWANIE HASEŁ) ---
        try {
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addFilter(new LogFilter(this));

            noticeManager.sendLoggerSuccess();

        } catch (Exception e) {
            noticeManager.sendLoggerError(e);
        }

        try {
            Class.forName("pl.dawcou.astralogin.system.LoginUtils");
        } catch (ClassNotFoundException ignored) {}

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        // Ten zajmuje się Join, Quit i PreLogin
        getServer().getPluginManager().registerEvents(loginListeners, this);

        // Ten zajmuje się blokowaniem niszczenia bloków, ruchu itp. dla niezalogowanych
        getServer().getPluginManager().registerEvents(new LoginBlocks(this), this);

        // Komendy dla graczy
        registerCommand("zarejestruj", loginSystem);
        registerCommand("zaloguj", loginSystem);
        registerCommand("zmienhaslo", passwordManager, passwordManager);
        registerCommand("pin", pinManager, pinManager);
        registerCommand("niepamietamhasla", passwordManager, passwordManager);
        registerCommand("wyloguj", accountCommand);

        // Komendy administracyjne
        registerCommand("astralogin", this, this);
        registerCommand("zresetujhaslo", passwordManager);
        registerCommand("zresetujpin", pinManager);
        registerCommand("zresetujip", ipManager);
        registerCommand("konto", accountCommand);
        registerCommand("zresetujkonto", accountCommand);
        registerCommand("listaip", accountCommand);
        registerCommand("zaufanieip", ipTrustManager, ipTrustManager);
        registerCommand("listakont", accountCommand);
        registerCommand("przenieskonto", accountCommand);
        registerCommand("spawnlogowania", spawnManager, spawnManager);

        registerCommand("2fa", twoFactorCommand, twoFactorCommand);
        registerCommand("zresetuj2fa", twoFactorCommand);

        sessionManager.loadSessionsFromConfig();
        sessionManager.load2FAFromConfig();

        // Zapisuje sesje i sprawdza czy można wyczyścić mapy z premium graczami co 10 minut
        // 1. Zapisuje sesje i sprawdza czy można wyczyścić mapy z premium graczami
        schedulerManager.runAsync(() -> {
            if (sessionManager != null) {
                sessionManager.saveSessionsToConfig();
            }
            premiumManager.cleanCache();
        });

        // 2. Odpala tworzenie backupów co 15 minut
        schedulerManager.runAsyncRepeating(
                () -> backupManager.createBackup(),
                1,
                15,
                TimeUnit.MINUTES
        );

        // 3. LOGO STARTOWE I SPRAWDZANIE WERSJI
        schedulerManager.runAsync(() -> {
            // Logo zawsze przy starcie
            noticeManager.sendStartupLogo();

            // Sprawdzanie aktualizacji
            if (getConfig().getBoolean("settings.check-updates", true)) {
                updateChecker.checkForUpdates(Bukkit.getConsoleSender());
            } else {
                noticeManager.sendVersionOk();
            }
        });
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
        }

        if (loginListeners == null) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            loginListeners.handleQuit(p);
        }

        languageManager.printMissingKeys();

        noticeManager.sendShutdownLogo();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(languageManager.getWithPrefix("general.no-permission"));
                    return true;
                }
                reloadConfig();
                IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 4);
                languageManager.reload();
                ipTrustManager.reload();
                reload();
                sender.sendMessage(getLanguageManager().getWithPrefix("general.reload-success"));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                sender.sendMessage(languageManager.parseToLegacy("<gray>------------ " + AstraLogin.PREFIX + " <gray>----------"));
                sender.sendMessage("§aPlugin created by: §eDawcoU");
                sender.sendMessage("§aPlugin version: §ev" + getDescription().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 DawcoU All rights reserved");
                sender.sendMessage("§7-----------------------");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("astralogin") || cmd.equalsIgnoreCase("al")) {
            if (args.length == 1) {
                hints.add("info");
                if (sender.hasPermission("astralogin.reload")) hints.add("reload");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(completer);
        }
    }
}