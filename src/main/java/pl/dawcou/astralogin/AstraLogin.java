package pl.dawcou.astralogin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.SessionManager;
import pl.dawcou.astralogin.auth.manage.*;
import pl.dawcou.astralogin.auth.accounts.AccountManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnManager;
import pl.dawcou.astralogin.commands.*;
import pl.dawcou.astralogin.auth.security.passwords.PINManager;
import pl.dawcou.astralogin.auth.security.passwords.PasswordManager;
import pl.dawcou.astralogin.auth.security.premium.PremiumManager;
import pl.dawcou.astralogin.auth.security.premium.listener.PremiumProtocolListener;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;
import pl.dawcou.astralogin.file.BackupManager;
import pl.dawcou.astralogin.file.FilesUpdater;
import pl.dawcou.astralogin.file.converters.MigrationManager;
import pl.dawcou.astralogin.listeners.LoginListeners;
import pl.dawcou.astralogin.listeners.TechnicalListeners;
import pl.dawcou.astralogin.logging.LogFilter;
import pl.dawcou.astralogin.logging.LogManager;
import pl.dawcou.astralogin.auth.security.attempts.AttemptManager;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.system.LanguageManager;
import pl.dawcou.astralogin.system.NoticeManager;
import pl.dawcou.astralogin.system.SchedulerManager;
import pl.dawcou.astralogin.system.UpdateChecker;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.system.tasks.SecurityReminderTask;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AstraLogin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ----------------------------------------------------------------------------------------------------
    // PREFIXY
    // ----------------------------------------------------------------------------------------------------
    public static final String PREFIX = "<gradient:#0055FF:#33CCFF:#33CCFF:#33CCFF:#0055FF>[AstraLogin]</gradient>";
    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    // ----------------------------------------------------------------------------------------------------
    // POLA GLOWNE I KONFIGURACYJNE
    // ----------------------------------------------------------------------------------------------------

    private static AstraLogin instance;
    private BukkitAudiences adventure;
    private KeyPair keyPair;

    private boolean debugEnabled;
    private boolean devMockMode;

    // ----------------------------------------------------------------------------------------------------
    // MANAGEROWIE SYSTEMOWI I DANYCH
    // ----------------------------------------------------------------------------------------------------

    private SchedulerManager schedulerManager;
    private LanguageManager languageManager;
    private AccountManager accountManager;
    private LogManager logManager;
    private BackupManager backupManager;
    private FilesUpdater filesUpdater;
    private UpdateChecker updateChecker;

    // ----------------------------------------------------------------------------------------------------
    // AUTORYZACJA I BEZPIECZENSTWO
    // ----------------------------------------------------------------------------------------------------

    private LoginSystem loginSystem;
    private PasswordManager passwordManager;
    private PINManager pinManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private TwoFactorManager twoFactorManager;
    private PremiumManager premiumManager;
    private IPManager ipManager;
    private IPTrustManager ipTrustManager;

    // ----------------------------------------------------------------------------------------------------
    // MEHANIKI ROZGRYWKI I INTERFEJS
    // ----------------------------------------------------------------------------------------------------

    private SpawnManager spawnManager;
    private InventoryManager inventoryManager;
    private NoticeManager noticeManager;
    private SecurityReminderTask securityReminderTask;

    // ----------------------------------------------------------------------------------------------------
    // PROTOKOLY, NASLUCHIWACZE I KOMENDY
    // ----------------------------------------------------------------------------------------------------

    private ProtocolManager protocolManager;
    private PremiumProtocolListener premiumProtocolListener;

    private LoginListeners loginListeners;
    private TechnicalListeners technicalListeners;

    private PasswordsCommand passwordsCommand;
    private PINCommand pinCommand;
    private IPResetCommand ipResetCommand;
    private IPManagerCommand iPManagerCommand;
    private IPTrustCommand iPTrustCommand;
    private LoginSpawnCommand loginSpawnCommand;

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - SYSTEMOWE I GLOWNE
    // ----------------------------------------------------------------------------------------------------

    public static AstraLogin getInstance() { return instance; }
    public BukkitAudiences getAdventure() { return this.adventure; }
    public KeyPair getKeyPair() { return keyPair; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isDevMockMode() { return devMockMode; }

    public SchedulerManager getSchedulerManager() { return schedulerManager; }
    public BackupManager getBackupManager() { return backupManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public AccountManager getAccountManager() { return accountManager; }
    public LogManager getLogManager() { return logManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - AUTORYZACJA I BEZPIECZENSTWO
    // ----------------------------------------------------------------------------------------------------

    public LoginSystem getLoginSystem() { return loginSystem; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public PINManager getPinManager() { return pinManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }
    public PremiumManager getPremiumManager() { return premiumManager; }
    public IPManager getIPManager() { return ipManager; }
    public IPTrustManager getIpTrustManager() { return ipTrustManager; }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - MECHANIKI I INTERFEJS
    // ----------------------------------------------------------------------------------------------------

    public SpawnManager getSpawnManager() { return spawnManager; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public NoticeManager getNoticeManager() { return noticeManager; }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - PROTOKOLY I KOMENDY
    // ----------------------------------------------------------------------------------------------------

    public ProtocolManager getProtocolManager() { return protocolManager; }
    public PremiumProtocolListener getPremiumProtocolListener() { return premiumProtocolListener; }

    // TODO: implement ultra advanced quantum potato engine with 9000% more binary and RTX ray tracing
    // Security is more important than backdoors or worms, so you, the reader, move on and don't worry about it.

    @Override
    public void onEnable() {
        if (Bukkit.getOnlineMode()) {
            getLogger().warning("Server is running in online-mode. Premium Login has been disabled because Mojang already verifies player accounts");
        }

        int pluginId = 31501;
        new Metrics(this, pluginId);

        instance = this;
        this.adventure = BukkitAudiences.create(this);

        // 1. Pliki na dysk i podstawowe wsparcie
        saveDefaultConfig();

        debugEnabled = getConfig().getBoolean("settings.debug.enabled", false);
        devMockMode = getConfig().getBoolean("settings.debug.dev-mock-mode", false);

        if (devMockMode) {
            getLogger().warning("==============================");
            getLogger().warning("WARNING! DevMockMode is enabled.");
            getLogger().warning("Make sure this option is not used on a production server.");
            getLogger().warning("This mode bypasses certain features for testing purposes.");
            getLogger().warning("==============================");
        }

        schedulerManager = new SchedulerManager(this);
        backupManager = new BackupManager(this);
        loginSystem = new LoginSystem(this);

        // 2. Inicjalizacja języka i powiadomień
        languageManager = new LanguageManager(this);
        languageManager.bootstrap(); // Ładujemy wstępnie języki, aby komunikaty w NoticeManager działały bezpiecznie
        noticeManager = new NoticeManager(this);

        // 3. Migracje danych
        new MigrationManager(this).migrate();

        // 4. Aktualizator plików i pełne przeładowanie języka
        filesUpdater = new FilesUpdater(this);
        filesUpdater.check();

        languageManager.reload();

        // 5. Diagnostyka
        logManager = new LogManager(this);
        updateChecker = new UpdateChecker(this);

        // 6. Inicjalizacja logiki i menedżerów
        twoFactorManager = new TwoFactorManager(this);
        accountManager = new AccountManager(this);
        inventoryManager = new InventoryManager(this);
        spawnManager = new SpawnManager(this);

        // --- MANAGERY ---
        // --- KOMENDY HASŁA ---
        passwordManager = new PasswordManager(this);
        passwordsCommand = new PasswordsCommand(this);

        // --- KOMENDY PIN ---
        pinManager = new PINManager(this);
        pinCommand = new PINCommand(this, pinManager);

        // --- KOMENDY PIN ---
        ipTrustManager = new IPTrustManager(this);
        iPTrustCommand = new IPTrustCommand(this, ipTrustManager);

        // --- KOMENDY LOGIN SPAWNA ---
        loginSpawnCommand = new LoginSpawnCommand(this);

        // --- KOMENDY IP ---
        IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 2);
        ipManager = new IPManager(this);
        ipResetCommand = new IPResetCommand(this, ipManager);
        iPManagerCommand = new IPManagerCommand(this);

        // --- POZOSTAŁE I ZADANIA ---
        sessionManager = new SessionManager(this);
        attemptManager = new AttemptManager(this);
        premiumManager = new PremiumManager(this);
        securityReminderTask = new SecurityReminderTask(this);
        securityReminderTask.start();

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
        technicalListeners = new TechnicalListeners(this);
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

        } catch (Exception e) {
            noticeManager.sendLoggerError(e);
            e.printStackTrace();
        }

        try {
            Class.forName("pl.dawcou.astralogin.system.LoginUtils");
        } catch (ClassNotFoundException ignored) {}

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        getServer().getPluginManager().registerEvents(loginListeners, this);
        getServer().getPluginManager().registerEvents(technicalListeners, this);

        // Ten zajmuje się blokowaniem niszczenia bloków, ruchu itp. dla niezalogowanych
        getServer().getPluginManager().registerEvents(new LoginBlocks(this), this);

        // Komendy dla graczy
        registerCommand("zarejestruj", loginSystem);
        registerCommand("zaloguj", loginSystem);
        registerCommand("zmienhaslo", passwordsCommand, passwordsCommand);
        registerCommand("pin", pinCommand, pinCommand);
        registerCommand("niepamietamhasla", passwordsCommand, passwordsCommand);
        registerCommand("wyloguj", accountCommand);

        // Komendy administracyjne
        registerCommand("astralogin", this, this);
        registerCommand("zresetujhaslo", passwordsCommand);
        registerCommand("zresetujpin", pinCommand);
        registerCommand("zresetujip", ipResetCommand);
        registerCommand("konto", accountCommand);
        registerCommand("zresetujkonto", accountCommand);
        registerCommand("listaip", accountCommand);
        registerCommand("zaufanieip", iPTrustCommand, iPTrustCommand);
        registerCommand("ipmanager", iPManagerCommand, iPManagerCommand);
        registerCommand("listakont", accountCommand);
        registerCommand("przenieskonto", accountCommand);
        registerCommand("spawnlogowania", loginSpawnCommand, loginSpawnCommand);

        registerCommand("2fa", twoFactorCommand, twoFactorCommand);
        registerCommand("zresetuj2fa", twoFactorCommand);

        sessionManager.loadSessionsFromConfig();
        sessionManager.load2FAFromConfig();

        // 1. Zapisuje sesje i sprawdza czy można wyczyścić mapy z premium graczami
        schedulerManager.runAsync(() -> {
            if (sessionManager != null) {
                sessionManager.saveSessionsToConfig();
            }
            premiumManager.cleanCache();
        });

        // 2. Odpala tworzenie backupów co 15 minut
        schedulerManager.runAsyncRepeating(
                () -> backupManager.createBackup(false),
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

        // Gracze
        if (loginListeners != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                loginListeners.handleQuit(p);
            }
        }

        // Dopiero teraz rozbrajamy bombę ewentualne debugi i wiadomość końcowa
        if (ipManager != null && ipManager.getIpBanManager() != null) {
            ipManager.getIpBanManager().saveBans();
        }

        if (languageManager != null) {
            languageManager.printMissingKeys();
        }

        if (noticeManager != null) {
            noticeManager.sendShutdownLogo();
        }
    }

    private void reload() {
        reloadConfig();

        IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 2);
        debugEnabled = getConfig().getBoolean("settings.debug.enabled", false);
        devMockMode = getConfig().getBoolean("settings.debug.dev-mock-mode", false);

        languageManager.reload();

        ipTrustManager.reload();
        ipManager.reload();

        securityReminderTask.start();

        spawnManager.reload();
        inventoryManager.reload();
        passwordManager.reload();
        pinManager.reload();
        sessionManager.reload();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {

            // Brak argumentów lub komenda /al help /al pomoc
            if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("pomoc")))) {
                noticeManager.sendHelp(sender);
                return true;
            }

            // Komenda /al reload
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(languageManager.getWithPrefix("general.no-permission"));
                    return true;
                }
                reload();
                sender.sendMessage(getLanguageManager().getWithPrefix("general.reload-success"));
                return true;
            }

            // Komenda /al info
            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                String prefix = (sender instanceof ConsoleCommandSender) ? PREFIX2 : PREFIX;

                sender.sendMessage(languageManager.parseToLegacy("<gray>------------ " + prefix + " <gray>----------"));
                sender.sendMessage("§aPlugin created by: §e " + getAuthor());
                sender.sendMessage("§aPlugin version: §ev" + getDescription().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 " + getAuthor() + " All rights reserved");
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
                hints.add("help");
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

    public String getAuthor() {
        return "DawcoU";
    }
}