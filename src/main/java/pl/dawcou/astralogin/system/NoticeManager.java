package pl.dawcou.astralogin.system;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import pl.dawcou.astralogin.AstraLogin;

public class NoticeManager {

    private final AstraLogin plugin;

    public NoticeManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // --- POMOCNICZE METODY DO PREFIXU I JĘZYKA ---

    private String getLang() {
        return plugin.getLanguageManager().getLang();
    }

    private String getPrefix(CommandSender sender) {
        String rawPrefix = (sender instanceof ConsoleCommandSender) ? AstraLogin.PREFIX2 : AstraLogin.PREFIX;
        return plugin.getLanguageManager().parseToLegacy(rawPrefix);
    }

    private String getConsolePrefix() {
        return getPrefix(Bukkit.getConsoleSender());
    }

    // --- METODY POWIADOMIEŃ CONFIGU I SYSTEMOWE ---

    public void sendHelp(CommandSender sender) {
        String p = getPrefix(sender) + " ";
        boolean isPl = getLang().equalsIgnoreCase("pl");

        sender.sendMessage(p + "§b§l=== " + (isPl ? "AstraLogin System Pomocy" : "AstraLogin Help System") + " ===");
        sender.sendMessage(p + "§7Wersja: §f" + plugin.getDescription().getVersion() + " §7| Autor: §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + (isPl ? "Komendy Gracza:" : "Player Commands:"));
        sender.sendMessage(p + " §f/register <pass> <repeat> §7- " + (isPl ? "Rejestracja nowego konta" : "Register a new account"));
        sender.sendMessage(p + " §f/login <pass> §7- " + (isPl ? "Logowanie do gry" : "Log into the server"));
        sender.sendMessage(p + " §f/logout §7- " + (isPl ? "Wylogowanie się z serwera" : "Log out of the server"));
        sender.sendMessage(p + " §f/changepassword <old> <new> <repeat> §7- " + (isPl ? "Zmiana obecnego hasła" : "Change your current password"));
        sender.sendMessage(p + " §f/pin <set> <PIN/Auto> §7- " + (isPl ? "Wygenerowanie lub ustawienie PIN-u" : "Set or generate account PIN"));
        sender.sendMessage(p + " §f/forgotpass <PIN> §7- " + (isPl ? "Reset hasła za pomocą PIN-u" : "Reset password using PIN"));
        sender.sendMessage(p + " §f/2fa <setup/unsetup/code> §7- " + (isPl ? "Zarządzanie i weryfikacja 2FA" : "Manage and verify 2FA"));

        if (sender.hasPermission("astralogin.account") || sender.hasPermission("astralogin.reload")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + (isPl ? "Komendy Administracji:" : "Admin Commands:"));
            sender.sendMessage(p + " §f/account <player> §7- " + (isPl ? "Zaawansowane statystyki konta" : "View advanced account stats"));
            sender.sendMessage(p + " §f/accountslist §7- " + (isPl ? "Lista wszystkich kont AstraLogin" : "View all AstraLogin accounts"));
            sender.sendMessage(p + " §f/resetpassword <player> §7- " + (isPl ? "Usuwa hasło gracza" : "Deletes a player's password"));
            sender.sendMessage(p + " §f/resetip <player> §7- " + (isPl ? "Resetuje adres IP gracza" : "Resets the player's IP address"));
            sender.sendMessage(p + " §f/resetpin <player> §7- " + (isPl ? "Resetuje PIN gracza" : "Resets the player's PIN"));
            sender.sendMessage(p + " §f/resetaccount <player> §7- " + (isPl ? "Czyszczenie wszystkich danych gracza" : "Wipes all player data"));
            sender.sendMessage(p + " §f/moveaccount <old> <new> §7- " + (isPl ? "Przenoszenie danych na inne konto" : "Transfers player data to another account"));
            sender.sendMessage(p + " §f/loginspawn <setspawn/delspawn> <type> §7- " + (isPl ? "Ustawianie i usuwanie spawnu" : "Sets and removes login spawn"));
            sender.sendMessage(p + " §f/ipmanager <info/unban/bypass/unbypass> <player> §7- " + (isPl ? "Zarządzanie blokadami IP" : "Manage IP bans and bypass"));
            sender.sendMessage(p + " §f/iptrust <info/set/reset> <IP> <score> §7- " + (isPl ? "Reputacja IP gracza" : "Shows and manages IP reputation"));
            sender.sendMessage(p + " §f/astralogin reload §7- " + (isPl ? "Przeładowanie konfiguracji pluginu" : "Reloads plugin configuration"));
        }

        sender.sendMessage(p + "§b§l=================================");
    }

    public void sendConfigUpdateNotice() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aPomyślnie dopisano brakujące linijki do configu" : "§aSuccessfully added missing lines to the config";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas zapisu configu: " : "§cError while saving config: ";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + error);
    }

    public void sendPlayerLocationReadError(String playerName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Błąd podczas odczytu pozycji dla " + playerName
                : "Error while reading location for " + playerName;
        plugin.getLogger().severe(msg);
    }

    public void sendLoggerError(Exception e) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "KRYTYCZNY BŁĄD: Nie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli Błąd: " + e.getMessage()
                : "CRITICAL ERROR: Failed to activate log filter! Passwords may be visible in console Error: " + e.getMessage();
        plugin.getLogger().severe(msg);
    }

    public void sendPremiumCheckError(String player, Exception e) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się zweryfikować gracza: (" + player + ") Błąd: " + e.getMessage()
                : "Failed to verify player: (" + player + ") Error: " + e.getMessage();
        plugin.getLogger().warning(msg);
    }

    public void sendPremiumFastCheckError(String player, Exception e) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się zweryfikować gracza w bazie Mojang: (" + player + ") Błąd: " + e.getMessage()
                : "Could not verify player in Mojang database: (" + player + ") Error: " + e.getMessage();
        plugin.getLogger().warning(msg);
    }

    public void sendLogSaveError(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się zapisać logu bezpieczeństwa do pliku: " + fileName
                : "Failed to save security log to file: " + fileName;
        plugin.getLogger().severe(msg);
    }

    public void sendBackupSaveError(String error) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się wykonać automatycznej kopii zapasowej, Błąd:"
                : "Automatic backup failed, Error:";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendBackupSave(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aPomyślnie wykonano automatyczną kopię zapasową w pliku: §e" :
                "§aAutomatic backup to file successfully completed: §e";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    // --- MIGRACJA ---

    public void sendMigrationNotice(String oldName, String newName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§6Migracja: §f" + oldName + " §7-> §f" + newName + "..."
                : "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSuccessMigrationNotice(String name) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aPlik/Sekcja §f" + name + " §azostała pomyślnie przeniesiona!"
                : "§aFile/Section §f" + name + " §ahas been successfully migrated!";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendErrorMigrationNotice(String action) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§cBłąd podczas migracji: §f" + action
                : "§cError during migration: §f" + action;
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSessionsLoaded(int count) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§eWczytano §6" + count + " §eaktywnych sesji z pliku" :
                "§eLoaded §6" + count + " §eactive sessions from file";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aDodano brakujące linijki w pliku językowym:" :
                "§aAdded missing lines in the language file";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się zaktualizować pliku językowego (" + fileName + "):"
                : "Failed to update language file (" + fileName + "): ";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = getLang().equalsIgnoreCase("pl") ? "   §6Wersja: " : "   §6Version: ";
        String status = getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = getLang().equalsIgnoreCase("pl") ? "   §6Autor: §e" : "   §6Author: §e";
        String statusLabel = getLang().equalsIgnoreCase("pl") ? "   §6Status: " : "   §6Status: ";

        String review = getLang().equalsIgnoreCase("pl")
                ? "§bPodoba się plugin? Zostaw opinię na Discord'zie!"
                : "§bLike the plugin? Leave a review on Discord!";

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage(version + "§ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + plugin.getAuthor());
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = getLang().equalsIgnoreCase("pl") ? "§eDziękujemy, że z nas korzystasz! Do zobaczenia!" : "§eThanks for choosing us! See you next time!";

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // --- METODY POWIADOMIEŃ WERSJI I AKTUALIZACJI ---

    public void sendVersionOk() {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aAstraLogin jest aktualny §f(§ev" + plugin.getDescription().getVersion() + "§f)"
                : "§aAstraLogin is up to date §f(§ev" + plugin.getDescription().getVersion() + "§f)";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendExperimentalNotice(CommandSender target) {
        String devTitle = getLang().equalsIgnoreCase("pl")
                ? "§bUżywasz eksperymentalną wersję: §fv"
                : "§bYou are using an experimental version: §fv";
        String warning = getLang().equalsIgnoreCase("pl")
                ? "§cUżywaj tylko dla testów! kod jest w fazie rozwoju!"
                : "§cUse only for testing! the code is in development!";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPreReleaseNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§6[Pre-Release] §eDostępna jest wersja testowa: §b" + plugin.getDescription().getVersion()
                : "§6[Pre-Release] §eTest version available: §b" + plugin.getDescription().getVersion();
        String info = getLang().equalsIgnoreCase("pl")
                ? "§cUwaga: Wersja wyłącznie do celów testowych! Może zawierać błędy."
                : "§cNotice: For testing purposes only! May contain bugs.";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title);
        target.sendMessage(info);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionDevNotice(CommandSender target, String latestStable) {
        String devTitle = getLang().equalsIgnoreCase("pl")
                ? "§bUżywasz nowszej wersji nie publicznej: §fv"
                : "§bYou are using a newer, non-public version: §fv";
        String stableInfo = getLang().equalsIgnoreCase("pl")
                ? "§eNajnowsza publiczna wersja AstraLogin to: §fv"
                : "§eThe latest public version of AstraLogin is: §fv";
        String warning = getLang().equalsIgnoreCase("pl")
                ? "§cUważaj na błędy, kod jest w fazie rozwoju!"
                : "§cWatch out for bugs, the code is in development!";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(stableInfo + latestStable);
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§cDostępna jest WIELKA aktualizacja AstraLogin: §fv"
                : "§cA MAJOR AstraLogin update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendMinorUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§eDostępna jest nowa aktualizacja AstraLogin: §fv"
                : "§eA new AstraLogin update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendPatchUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§bDostępna jest poprawka AstraLogin: §fv"
                : "§bAn AstraLogin bug fix is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się sprawdzić aktualizacji"
                : "Failed to check for updates";
        plugin.getLogger().warning(msg);
    }
}