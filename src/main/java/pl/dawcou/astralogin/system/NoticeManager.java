package pl.dawcou.astralogin.system;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import pl.dawcou.astralogin.auth.AstraLogin;

public class NoticeManager {

    private final AstraLogin plugin;
    private final String PREFIX;
    private final String PREFIX2;

    public NoticeManager(AstraLogin plugin) {
        this.plugin = plugin;
        PREFIX = AstraLogin.PREFIX;
        PREFIX2 = AstraLogin.PREFIX2;
    }

    public void sendConfigUpdateNotice() {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§aPomyślnie dopisano brakujące linijki do configu" : "§aSuccessfully added missing lines to the config";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas zapisu configu: " : "§cError while saving config: ";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + error);
    }

    public void sendPlayerLocationReadError(String playerName) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas odczytu pozycji dla " + playerName : "§cError while reading location for " + playerName;
        plugin.getLogger().severe(msg);
    }

    public void sendSpawnSaveError() {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cNie można zapisać pliku spawns/locations.yml!" : "§cCould not save spawns/locations.yml file!";
        plugin.getLogger().severe(msg);
    }

    public void sendLoggerSuccess() {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§aFiltr haseł został pomyślnie aktywowany" :
                "§aPassword filter has been successfully activated";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLoggerError(Exception e) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§4KRYTYCZNY BŁĄD: §cNie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli Błąd: " + e.getMessage() :
                "§4CRITICAL ERROR: §cFailed to activate log filter! Passwords may be visible in console Error: " + e.getMessage();
        plugin.getLogger().severe((msg)
        );
    }

    public void sendPremiumCheckError(String player, Exception e) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zweryfikować gracza: §6(" + player + ") §cBłąd: " + e.getMessage() :
                "§cFailed to verify player: §6(" + player + ") §cError: " + e.getMessage();
        plugin.getLogger().warning((msg)
        );
    }

    public void sendPremiumFastCheckError(String player, Exception e) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zweryfikować gracza w bazie Mojang: §6(" + player + ") §cBłąd: " + e.getMessage() :
                "§cCould not verify player in Mojang database: §6(" + player + ") §cError: " + e.getMessage();
        plugin.getLogger().warning((msg)
        );
    }

    public void sendLogSaveError(String fileName) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zapisać logu bezpieczeństwa do pliku: §e" + fileName :
                "§cFailed to save security log to file: §e" + fileName;
        plugin.getLogger().severe(msg);
    }

    public void sendBackupSaveError(String error) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się wykonać automatycznej kopi zapasowej, Błąd:" :
                "§cAutomatic backup failed, Error:";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendBackupSave(String fileName) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§aPomyślnie wykonano automatyczną kopię zapasową w pliku:§e" :
                "§aAutomatic backup to file successfully completed:§e";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + " " + fileName);
    }

    public void sendMigrationError(String fileName) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zmodyfikować pliku przy migracji:" :
                "§cFailed to modify file during migration:";
        plugin.getLogger().warning(msg + " " + fileName);
    }

    public void sendSessionsLoaded(int count) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§eWczytano §6" + count + " §eaktywnych sesji z pliku" :
                "§eLoaded §6" + count + " §eactive sessions from file";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§aDodano brakujące linijki w pliku językowym:" :
                "§aAdded missing lines in the language file" ;
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zaktualizować pliku językowego (" + fileName + "):" :
                "§cFailed to update language file (" + fileName + "): ";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "   §6Wersja" : "   §6Version";
        String status = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "   §6Autor: §e" : "   §6Author: §e";
        String statusLabel = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "   §6Status: " : "   §6Status: ";

        String review = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§bPodoba się plugin? Zostaw opinię na Discord'zie!"
                : "§bLike the plugin? Leave a review on Discord!";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(version + " §ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§eDziękujemy, że z nas korzystasz! Do zobaczenia!" : "§eThanks for choosing us! See you next time!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // METODY JĘZYKOWE PONIŻEJ
    public void sendVersionOk() {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§aAstraLogin jest aktualny §f(§ev" + plugin.getDescription().getVersion() + "§f)" : "§aAstraLogin is up to date §f(§ev" + plugin.getDescription().getVersion() + "§f)";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendExperimentalNotice(CommandSender target) {
        String devTitle = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§bUżywasz eksperymentalną wersję: §fv" : "§bYou are using an experimental version: §fv";
        String warning = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cUżywaj tylko dla testów! kod jest w fazie rozwoju!" : "§cUse only for testing! the code is in development!";

        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPreReleaseNotice(CommandSender target, String version) {
        String title = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§6[Pre-Release] §eDostępna jest wersja testowa: §b" + plugin.getDescription().getVersion() : "§6[Pre-Release] §eTest version available: §b" + plugin.getDescription().getVersion();
        String info = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cUwaga: Wersja wyłącznie do celów testowych! Może zawierać błędy." : "§cNotice: For testing purposes only! May contain bugs.";

        String download = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title);
        target.sendMessage(info);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionDevNotice(CommandSender target, String latestStable) {
        String devTitle = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§bUżywasz nowszej wersji nie publicznej: §fv" : "§bYou are using a newer, non-public version: §fv";
        String stableInfo = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§eNajnowsza publiczna wersja AstraLogin to: §fv" : "§eThe latest public version of AstraLogin is: §fv";
        String warning = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl") ? "§cUważaj na błędy, kod jest w fazie rozwoju!" : "§cWatch out for bugs, the code is in development!";
        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(stableInfo + latestStable);
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String title = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§cDostępna jest WIELKA aktualizacja AstraLogin: §fv"
                : "§cA MAJOR AstraLogin update is available: §fv";

        String download = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendMinorUpdateNotice(CommandSender target, String version) {
        String title = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§eDostępna jest nowa aktualizacja AstraLogin: §fv"
                : "§eA new AstraLogin update is available: §fv";

        String download = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendPatchUpdateNotice(CommandSender target, String version) {
        String title = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§bDostępna jest poprawka AstraLogin: §fv"
                : "§bAn AstraLogin bug fix is available: §fv";

        String download = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg = plugin.getLanguageManager().getLang().equalsIgnoreCase("pl")
                ? "§cNie udało się sprawdzić aktualizacji na Modrinth"
                : "§cFailed to check for updates on Modrinth";
        plugin.getLogger().warning(msg);
    }
}