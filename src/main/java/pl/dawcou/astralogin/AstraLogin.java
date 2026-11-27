package pl.dawcou.astralogin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import pl.dawcou.astralogin.api.AstraLoginAPI;
import pl.dawcou.astralogin.api.AstraLoginAPIImpl;
import pl.dawcou.astralogin.auth.EmailSender;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.security.BackendManager;
import pl.dawcou.astralogin.auth.security.captcha.mode.chat.ChatCaptchaListeners;
import pl.dawcou.astralogin.auth.security.captcha.mode.gui.GuiCaptchaListeners;
import pl.dawcou.astralogin.auth.security.captcha.CaptchaManager;
import pl.dawcou.astralogin.auth.security.captcha.mode.map.MapCaptchaListeners;
import pl.dawcou.astralogin.auth.security.encryption.DataEncryptionManager;
import pl.dawcou.astralogin.auth.security.encryption.EncryptionKeyManager;
import pl.dawcou.astralogin.auth.security.suspicious.SuspiciousManager;
import pl.dawcou.astralogin.auth.sessions.SessionManager;
import pl.dawcou.astralogin.auth.manage.*;
import pl.dawcou.astralogin.auth.AccountManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnManager;
import pl.dawcou.astralogin.auth.password.PINManager;
import pl.dawcou.astralogin.auth.password.PasswordManager;
import pl.dawcou.astralogin.auth.security.premium.PremiumManager;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;
import pl.dawcou.astralogin.commands.account.*;
import pl.dawcou.astralogin.commands.email.EmailCommand;
import pl.dawcou.astralogin.commands.email.ResetEmailCommand;
import pl.dawcou.astralogin.commands.ip.ResetIPCommand;
import pl.dawcou.astralogin.commands.password.ChangePassCommand;
import pl.dawcou.astralogin.commands.password.ForgotPassCommand;
import pl.dawcou.astralogin.commands.password.ResetPassCommand;
import pl.dawcou.astralogin.commands.pin.PINCommand;
import pl.dawcou.astralogin.commands.pin.ResetPINCommand;
import pl.dawcou.astralogin.commands.admin.*;
import pl.dawcou.astralogin.commands.ip.IPManagerCommand;
import pl.dawcou.astralogin.commands.ip.IPTrustCommand;
import pl.dawcou.astralogin.commands.twofactor.TwoFactorCommands;
import pl.dawcou.astralogin.data.GlobalDataManager;
import pl.dawcou.astralogin.data.PlayerDataManager;
import pl.dawcou.astralogin.file.BackupManager;
import pl.dawcou.astralogin.file.FilesUpdater;
import pl.dawcou.astralogin.file.converters.MigrationManager;
import pl.dawcou.astralogin.listeners.LoginListeners;
import pl.dawcou.astralogin.listeners.TechnicalListeners;
import pl.dawcou.astralogin.logging.LogFilter;
import pl.dawcou.astralogin.logging.LogManager;
import pl.dawcou.astralogin.auth.security.attempt.AttemptManager;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.modules.punishments.PunishmentModuleLoader;
import pl.dawcou.astralogin.system.LanguageManager;
import pl.dawcou.astralogin.system.NoticeManager;
import pl.dawcou.astralogin.system.SchedulerManager;
import pl.dawcou.astralogin.system.updater.AutoUpdater;
import pl.dawcou.astralogin.system.updater.UpdateChecker;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.system.seasonal.SeasonalEventManager;
import pl.dawcou.astralogin.system.tasks.SecurityReminderTask;
import pl.dawcou.astralogin.system.SoundManager;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.TimeUnit;

public class AstraLogin extends JavaPlugin implements Listener {

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
    private DataEncryptionManager encryptionManager;

    private boolean debugEnabled;
    private boolean devMockMode;

    // ----------------------------------------------------------------------------------------------------
    // MANAGEROWIE SYSTEMOWI I DANYCH
    // ----------------------------------------------------------------------------------------------------

    private PlayerDataManager playerDataManager;
    private GlobalDataManager globalDataManager;

    private PunishmentModuleLoader punishmentModuleLoader;
    private BackendManager backendManager;

    private SchedulerManager schedulerManager;
    private SoundManager soundManager;
    private LanguageManager languageManager;
    private AccountManager accountManager;
    private LogManager logManager;
    private BackupManager backupManager;
    private FilesUpdater filesUpdater;
    private UpdateChecker updateChecker;
    private AutoUpdater autoUpdater;

    // ----------------------------------------------------------------------------------------------------
    // AUTORYZACJA I BEZPIECZENSTWO
    // ----------------------------------------------------------------------------------------------------

    private LoginSystem loginSystem;
    private PasswordManager passwordManager;
    private PINManager pinManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private TwoFactorManager twoFactorManager;
    private EmailSender emailSender;
    private PremiumManager premiumManager;
    private IPManager ipManager;
    private IPTrustManager ipTrustManager;
    private CaptchaManager captchaManager;

    // ----------------------------------------------------------------------------------------------------
    // MECHANIKI ROZGRYWKI I INTERFEJS
    // ----------------------------------------------------------------------------------------------------

    private SpawnManager spawnManager;
    private InventoryManager inventoryManager;
    private NoticeManager noticeManager;
    private SecurityReminderTask securityReminderTask;

    private SeasonalEventManager seasonalEventManager;
    private SuspiciousManager suspiciousManager;

    // ----------------------------------------------------------------------------------------------------
    // NASLUCHIWACZE
    // ----------------------------------------------------------------------------------------------------
    private LoginListeners loginListeners;
    private TechnicalListeners technicalListeners;

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - SYSTEMOWE I GLOWNE
    // ----------------------------------------------------------------------------------------------------

    public static AstraLogin getInstance() { return instance; }
    public BukkitAudiences getAdventure() { return this.adventure; }
    public KeyPair getKeyPair() { return keyPair; }
    public DataEncryptionManager getEncryptionManager() { return encryptionManager; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isDevMockMode() { return devMockMode; }

    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }

    public GlobalDataManager getGlobalDataManager() { return globalDataManager; }

    public BackendManager getBackendManager() { return backendManager; }

    public LoginListeners getLoginListeners() { return loginListeners; }

    public SchedulerManager getSchedulerManager() { return schedulerManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public BackupManager getBackupManager() { return backupManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public AccountManager getAccountManager() { return accountManager; }
    public LogManager getLogManager() { return logManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public AutoUpdater getAutoUpdater() { return autoUpdater; }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - AUTORYZACJA I BEZPIECZENSTWO
    // ----------------------------------------------------------------------------------------------------

    public LoginSystem getLoginSystem() { return loginSystem; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public PINManager getPinManager() { return pinManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }
    public EmailSender getEmailSender() { return emailSender; }
    public PremiumManager getPremiumManager() { return premiumManager; }
    public IPManager getIPManager() { return ipManager; }
    public IPTrustManager getIpTrustManager() { return ipTrustManager; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - MECHANIKI I INTERFEJS
    // ----------------------------------------------------------------------------------------------------

    public SpawnManager getSpawnManager() { return spawnManager; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public NoticeManager getNoticeManager() { return noticeManager; }
    public SeasonalEventManager getSeasonalEventManager() { return seasonalEventManager; }
    public SuspiciousManager getSuspicionManager() { return suspiciousManager; }

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

        try {
            EncryptionKeyManager keyManager = new EncryptionKeyManager(getDataFolder().toPath());
            SecretKey encryptionKey = keyManager.loadOrCreateKey();

            encryptionManager = new DataEncryptionManager(encryptionKey);
        } catch (IOException | GeneralSecurityException exception) {
            getLogger().severe("Failed to initialize data encryption!");
            exception.printStackTrace();

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        schedulerManager = new SchedulerManager(this);
        soundManager = new SoundManager(this);
        backupManager = new BackupManager(this);

        loginSystem = new LoginSystem(this);

        // 2. Inicjalizacja języka i powiadomień
        languageManager = new LanguageManager(this);
        languageManager.bootstrap(); // Ładujemy wstępnie języki, aby komunikaty w NoticeManager działały bezpiecznie
        noticeManager = new NoticeManager(this);
        seasonalEventManager = new SeasonalEventManager(this);

        // 3. Migracje danych
        new MigrationManager(this).migrate();

        // 4. Menedżery plików
        playerDataManager = new PlayerDataManager(this);
        globalDataManager = new GlobalDataManager(this);

        // 5. Aktualizator plików i pełne przeładowanie języka
        filesUpdater = new FilesUpdater(this);
        filesUpdater.check();

        languageManager.reload();

        // 6. Diagnostyka
        logManager = new LogManager(this);
        updateChecker = new UpdateChecker(this, "sO4dBl28");
        autoUpdater = new AutoUpdater(this, "sO4dBl28");

        // 7. Inicjalizacja logiki i menedżerów
        emailSender = new EmailSender(this);
        twoFactorManager = new TwoFactorManager(this);
        accountManager = new AccountManager(this, playerDataManager);
        inventoryManager = new InventoryManager(this, playerDataManager);
        spawnManager = new SpawnManager(this, playerDataManager, globalDataManager);

        // --- MANAGERY ---
        passwordManager = new PasswordManager(this, playerDataManager);
        pinManager = new PINManager(this, playerDataManager);
        ipTrustManager = new IPTrustManager(this, globalDataManager);
        ipManager = new IPManager(this, playerDataManager, globalDataManager);

        // --- POZOSTAŁE I ZADANIA ---
        sessionManager = new SessionManager(this, playerDataManager);
        attemptManager = new AttemptManager(this);
        premiumManager = new PremiumManager(this);
        captchaManager = new CaptchaManager(this);

        securityReminderTask = new SecurityReminderTask(this);
        securityReminderTask.start();

        suspiciousManager = new SuspiciousManager(this);

        try {
            // Generujemy parę kluczy RSA (1024-bit) dla autoryzacji Premium
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            getLogger().severe("Failed to generate RSA keys: " + e.getMessage());
        }

        backendManager = new BackendManager(this);

        loginListeners = new LoginListeners(this);
        technicalListeners = new TechnicalListeners(this);

        // --- FILTRACJA LOGÓW (UKRYWANIE HASEŁ) ---
        try {
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addFilter(new LogFilter(this));

        } catch (Exception e) {
            noticeManager.sendLoggerError(e);
            e.printStackTrace();
        }

        // --- REJESTRACJA EVENTÓW I KOMEND ---
        getServer().getPluginManager().registerEvents(loginListeners, this);
        getServer().getPluginManager().registerEvents(technicalListeners, this);

        getServer().getPluginManager().registerEvents(new GuiCaptchaListeners(this), this);
        getServer().getPluginManager().registerEvents(new MapCaptchaListeners(this), this);
        getServer().getPluginManager().registerEvents(new ChatCaptchaListeners(this), this);

        getServer().getPluginManager().registerEvents(new LoginBlocks(this), this);

        // --- KOMENDY ---
        AstraLoginCommand astraLoginCommand = new AstraLoginCommand(this);
        LoginSpawnCommand loginSpawnCommand = new LoginSpawnCommand(this);

        ResetPassCommand resetPassCommand = new ResetPassCommand(this);
        ForgotPassCommand forgotPassCommand = new ForgotPassCommand(this);
        ChangePassCommand changePassCommand = new ChangePassCommand(this);

        PINCommand pinCommand = new PINCommand(this, pinManager);
        ResetPINCommand resetPINCommand = new ResetPINCommand(this, pinManager);

        AccountCommand accountCommand = new AccountCommand(this);
        ResetAccountCommand resetAccountCommand = new ResetAccountCommand(this);
        IPListCommand ipListCommand = new IPListCommand(this);
        AccountsListCommand accountsListCommand = new AccountsListCommand(this);
        MoveAccountCommand moveAccountCommand = new MoveAccountCommand(this);
        LogoutCommand logoutCommand = new LogoutCommand(this);

        TwoFactorCommands twoFactorCommands = new TwoFactorCommands(this, twoFactorManager, loginSystem);

        EmailCommand emailCommand = new EmailCommand(this, twoFactorManager);
        ResetEmailCommand resetEmailCommand = new ResetEmailCommand(this, twoFactorManager.getEmailManager());

        IPTrustCommand iPTrustCommand = new IPTrustCommand(this, ipTrustManager);
        ResetIPCommand resetIPCommand = new ResetIPCommand(this, ipManager);
        IPManagerCommand iPManagerCommand = new IPManagerCommand(this);

        // Komendy dla graczy
        registerCommand("zarejestruj", loginSystem);
        registerCommand("zaloguj", loginSystem);

        registerCommand("zmienhaslo", changePassCommand, changePassCommand);

        registerCommand("zresetujhaslo", resetPassCommand);

        registerCommand("niepamietamhasla", forgotPassCommand);

        registerCommand("pin", pinCommand, pinCommand);
        registerCommand("wyloguj", logoutCommand);
        registerCommand("2fa", twoFactorCommands, twoFactorCommands);
        registerCommand("mail", emailCommand, emailCommand);

        // Komendy administracyjne
        registerCommand("astralogin", astraLoginCommand, astraLoginCommand);
        registerCommand("zresetujhaslo", resetPassCommand);
        registerCommand("zresetujpin", resetPINCommand);
        registerCommand("zresetujip", resetIPCommand);
        registerCommand("zresetujkonto", resetAccountCommand);
        registerCommand("zresetuj2fa", twoFactorCommands);
        registerCommand("zresetujmail", resetEmailCommand);
        registerCommand("konto", accountCommand);
        registerCommand("listakont", accountsListCommand);

        registerCommand("listaip", ipListCommand);
        registerCommand("zaufanieip", iPTrustCommand, iPTrustCommand);
        registerCommand("ipmanager", iPManagerCommand, iPManagerCommand);

        registerCommand("przenieskonto", moveAccountCommand);
        registerCommand("spawnlogowania", loginSpawnCommand, loginSpawnCommand);

        sessionManager.loadAllSessions();

        // ----------------------------------------------------------------------------------------------------
        // Schedulery
        // ----------------------------------------------------------------------------------------------------

        // Zapis danych co 5 minut
        schedulerManager.runAsyncRepeating(
                () -> {
                    playerDataManager.saveAll();
                    globalDataManager.save();
                },
                5,
                5,
                TimeUnit.MINUTES
        );

        // Próba stworzenia backupu co 10 minut
        schedulerManager.runAsyncRepeating(
                () -> backupManager.createBackup(false),
                10,
                10,
                TimeUnit.MINUTES
        );

        // Czyszczenie śmieci
        schedulerManager.runAsyncRepeating(
                () -> {
                    premiumManager.cleanCache();
                    seasonalEventManager.cleanCache();
                    attemptManager.cleanCache();
                },
                30,
                30,
                TimeUnit.MINUTES
        );

        // ----------------------------------------------------------------------------------------------------

        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }

        if (globalDataManager != null) {
            globalDataManager.save();
        }

        noticeManager.sendStartupLogo();

        // Sprawdzanie aktualizacji
        if (getConfig().getBoolean("settings.update.check-updates", true)) {
            updateChecker.checkForUpdates(Bukkit.getConsoleSender());
        }

        punishmentModuleLoader = new PunishmentModuleLoader(this);
        punishmentModuleLoader.initialize();

        // Uruchamianie API
        AstraLoginAPIImpl apiImplementation = new AstraLoginAPIImpl(this);
        getServer().getServicesManager().register(
                AstraLoginAPI.class,
                apiImplementation,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );
    }

    @Override
    public void onDisable() {
        if (adventure != null) {
            adventure.close();
        }

        // Gracze
        if (loginListeners != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                loginListeners.handleQuit(p, true);
            }
        }

        // Dopiero teraz rozbrajamy bombę ewentualne debugi i wiadomość końcowa
        if (ipManager != null && ipManager.getIpBanManager() != null) {
            ipManager.getIpBanManager().saveBans();
        }

        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }

        if (globalDataManager != null) {
            globalDataManager.save();
        }

        if (languageManager != null) {
            languageManager.printMissingKeys();
        }

        if (noticeManager != null) {
            noticeManager.sendShutdownLogo();
        }
    }

    public void reload() {
        // 1. Przeładowanie pliku config.yml
        reloadConfig();

        // 2. Aktualizacja zmiennych podręcznych z configu
        debugEnabled = getConfig().getBoolean("settings.debug.enabled", false);
        devMockMode = getConfig().getBoolean("settings.debug.dev-mock-mode", false);

        // 3. Przeładowanie języka
        languageManager.reload();

        // 4. Przeładowanie menedżerów danych JSON (I/O)
        globalDataManager.reload();
        playerDataManager.reloadAll();

        // 5. Przeładowanie logiki i menedżerów systemowych
        ipTrustManager.reload();
        ipManager.reload();

        securityReminderTask.start();
        seasonalEventManager.loadEvents();

        sessionManager.reload();
        twoFactorManager.reload();
        premiumManager.cleanCache();

        punishmentModuleLoader.reload();
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