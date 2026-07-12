package pl.dawcou.astralogin.auth;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import pl.dawcou.astralogin.auth.manage.InventoryManager;
import pl.dawcou.astralogin.accounts.AccountManager;
import pl.dawcou.astralogin.accounts.AccountCommand;
import pl.dawcou.astralogin.file.BackupManager;
import pl.dawcou.astralogin.file.FilesConverter;
import pl.dawcou.astralogin.file.FilesUpdater;
import pl.dawcou.astralogin.logging.LogFilter;
import pl.dawcou.astralogin.logging.LogManager;
import pl.dawcou.astralogin.auth.manage.LoginBlocks;
import pl.dawcou.astralogin.auth.manage.LoginListeners;
import pl.dawcou.astralogin.auth.manage.SpawnManager;
import pl.dawcou.astralogin.auth.security.AttemptManager;
import pl.dawcou.astralogin.auth.security.IPManager;
import pl.dawcou.astralogin.system.LanguageManager;
import pl.dawcou.astralogin.system.NoticeManager;
import pl.dawcou.astralogin.system.UpdateChecker;
import pl.dawcou.astralogin.auth.twofactor.TwoFactorCommand;
import pl.dawcou.astralogin.auth.twofactor.TwoFactorManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AstraLogin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- PREFIXY I STAŁE ---
    public static final String PREFIX = "<gradient:#0055FF:#33CCFF:#33CCFF:#33CCFF:#0055FF>[AstraLogin]</gradient>";
    public static final String PREFIX2 = ("§9[§bAstraLogin§9]");

    // --- INSTANCJE MANAGERÓW (POLA) ---
    private LanguageManager languageManager;
    private InventoryManager inventoryManager;
    private LoginSystem loginSystem;
    private FilesUpdater filesUpdater;
    private LoginListeners loginListeners;
    private SpawnManager spawnManager;
    private PasswordManager passwordManager;
    private IPManager ipManager;
    private NoticeManager noticeManager;
    private SessionManager sessionManager;
    private AttemptManager attemptManager;
    private FilesConverter filesConverter;
    private LogManager logManager;
    private AccountManager accountManager;
    private BackupManager backupManager;
    private TwoFactorManager twoFactorManager;

    // --- GETTERY (Dostęp dla innych klas) ---
    public LanguageManager getLanguageManager() { return languageManager; }
    public PasswordManager getPasswordManager() { return passwordManager; }
    public LoginSystem getLoginSystem() { return loginSystem; }
    public void setLanguageManager(LanguageManager languageManager) { this.languageManager = languageManager; }
    public IPManager getIPManager() { return ipManager; }
    public NoticeManager getNoticeManager() {
        return noticeManager;
    }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public SpawnManager getSpawnManager() { return spawnManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AttemptManager getAttemptManager() { return attemptManager; }
    public LogManager getLogManager() { return logManager; }
    public AccountManager getAccountDataManager() { return accountManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }

    @Override
    public void onEnable() {
        // 1. Pliki na dysk
        saveDefaultConfig();

        int pluginId = 31501;
        new Metrics(this, pluginId);

        IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 4);

        // 2. Migracje danych (Muszą wykonać się przed jakimkolwiek odczytem przez managery)
        new FilesConverter(this).runAllMigrations();

        noticeManager = new NoticeManager(this);

        filesUpdater = new FilesUpdater(this);
        filesUpdater.check();

        // 3. Infrastruktura diagnostyczna i językowa
        logManager = new LogManager(this);
        languageManager = new LanguageManager(this);
        languageManager.reload(); // Ładujemy języki od razu, aby komunikaty były dostępne

        // 4. Inicjalizacja managerów logicznych
        twoFactorManager = new TwoFactorManager(this);
        accountManager = new AccountManager(this);
        inventoryManager = new InventoryManager(this);
        spawnManager = new SpawnManager(this);
        passwordManager = new PasswordManager(this);
        ipManager = new IPManager(this);
        sessionManager = new SessionManager(this);
        attemptManager = new AttemptManager(this);
        loginListeners = new LoginListeners(this);
        backupManager = new BackupManager(this);
        AccountCommand accountCommand = new AccountCommand(this);
        TwoFactorCommand twoFactorCommand = new TwoFactorCommand(this, twoFactorManager, loginSystem);

        // 5. Wczytywanie baz danych i cache
        passwordManager.reload();
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

        // Tworzenie serca pluginu - LoginSystem
        loginSystem = new LoginSystem(this);

        // --- 4. REJESTRACJA EVENTÓW I KOMEND ---
        // Ten zajmuje się Join, Quit i PreLogin
        getServer().getPluginManager().registerEvents(this.loginListeners, this);

        // Ten zajmuje się blokowaniem niszczenia bloków, ruchu itp. dla niezalogowanych
        getServer().getPluginManager().registerEvents(new LoginBlocks(this), this);

        // Komendy główne
        registerCommand("zarejestruj", loginSystem);
        registerCommand("zaloguj", loginSystem);
        registerCommand("astralogin", this, this);
        registerCommand("zresetujhaslo", passwordManager);
        registerCommand("zmienhaslo", passwordManager, passwordManager);

        // Komendy administracyjne
        registerCommand("zresetujip", ipManager);
        registerCommand("konto", accountCommand);
        registerCommand("zresetujkonto", accountCommand);
        registerCommand("listaip", accountCommand);
        registerCommand("listakont", accountCommand);
        registerCommand("przenieskonto", accountCommand);
        registerCommand("spawnlogowania", spawnManager, spawnManager);

        registerCommand("2fa", twoFactorCommand, twoFactorCommand);
        registerCommand("zresetuj2fa", twoFactorCommand);

        sessionManager.loadSessionsFromConfig();
        sessionManager.load2FAFromConfig();

        // Zapisuje sesje co 10 minut
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (sessionManager != null) {
                sessionManager.saveSessionsToConfig();
            }
        }, 10, 10, java.util.concurrent.TimeUnit.MINUTES);

        // Odpala tworzenie backupów co 15 minut
        getServer().getAsyncScheduler().runAtFixedRate(
                this,
                task -> backupManager.createBackup(),
                1,
                15,
                TimeUnit.MINUTES
        );

        // --- 5. LOGO STARTOWE I SPRAWDZANIE WERSJI ---
        // Odpalamy scheduler asynchroniczny, który najpierw sprawdzi internet, a na koniec wypluje logo i status wersji!
        getServer().getAsyncScheduler().runNow(this, task -> {
            // Najpierw sprawdzamy aktualizacje, jeśli opcja jest włączona
            if (getConfig().getBoolean("settings.check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = getDescription().getVersion();

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
                // Jeśli admin wyłączył sprawdzanie aktualizacji, po prostu drukujemy samo logo
                noticeManager.sendStartupLogo();
            }
        });
    }

    @Override
    public void onDisable() {
        if (this.loginListeners == null) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.loginListeners.handleQuit(p);
        }
        noticeManager.sendShutdownLogo();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("astralogin") || command.getName().equalsIgnoreCase("al")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogin.reload")) {
                    sender.sendMessage(languageManager.getWithPrefix("no-permission"));
                    return true;
                }
                reloadConfig();
                IPManager.ipCheckOctets = getConfig().getInt("security.ip-security.ip-check-octets", 4);
                setLanguageManager(new LanguageManager(this));
                sender.sendMessage(getLanguageManager().getWithPrefix("reload-success"));
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