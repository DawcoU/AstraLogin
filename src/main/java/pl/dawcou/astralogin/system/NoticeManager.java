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
        this.PREFIX = AstraLogin.PREFIX;
        this.PREFIX2 = AstraLogin.PREFIX2;
    }

    // Pomocnicza metoda do pobierania języka
    private String getLang() {
        return plugin.getConfig().getString("language", "pl");
    }

    public void sendConfigUpdateNotice() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aPomyślnie dopisano brakujące linijki do configu" : "§aSuccessfully added missing lines to the config";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas zapisu configu: " : "§cError while saving config: ";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + error);
    }

    public void sendPlayerLocationReadError(String playerName) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas odczytu pozycji dla " + playerName : "§cError while reading location for " + playerName;
        plugin.getLogger().severe(msg);
    }

    public void sendSpawnSaveError() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNie można zapisać pliku spawns/locations.yml!" : "§cCould not save spawns/locations.yml file!";
        plugin.getLogger().severe(msg);
    }

    public void sendLoggerSuccess() {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aFiltr haseł został pomyślnie aktywowany" :
                "§aPassword filter has been successfully activated";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLoggerError(Exception e) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§4KRYTYCZNY BŁĄD: §cNie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli Błąd: " + e.getMessage() :
                "§4CRITICAL ERROR: §cFailed to activate log filter! Passwords may be visible in console Error: " + e.getMessage();
        plugin.getLogger().severe((msg)
        );
    }

    public void sendPremiumCheckError(String player, Exception e) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zweryfikować gracza: §6(" + player + ") §cBłąd: " + e.getMessage() :
                "§cFailed to verify player: §6(" + player + ") §cError: " + e.getMessage();
        plugin.getLogger().warning((msg)
        );
    }

    public void sendLogSaveError(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zapisać logu bezpieczeństwa do pliku: §e" + fileName :
                "§cFailed to save security log to file: §e" + fileName;
        plugin.getLogger().severe(msg);
    }

    public void sendBackupSaveError(String error) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się wykonać automatycznej kopi zapasowej, Błąd:" :
                "§cAutomatic backup failed, Error:";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendBackupSave(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aPomyślnie wykonano automatyczną kopię zapasową w pliku:§e" :
                "§aAutomatic backup to file successfully completed:§e";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + " " + fileName);
    }

    public void sendMigrationError(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zmodyfikować pliku przy migracji:" :
                "§cFailed to modify file during migration:";
        plugin.getLogger().warning(msg + " " + fileName);
    }

    public void sendSessionsLoaded(int count) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§eWczytano §6" + count + " §eaktywnych sesji z pliku" :
                "§eLoaded §6" + count + " §eactive sessions from file";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aDodano brakujące linijki w pliku językowym:" :
                "§aAdded missing lines in the language file" ;
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zaktualizować pliku językowego (" + fileName + "):" :
                "§cFailed to update language file (" + fileName + "): ";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = getLang().equalsIgnoreCase("pl") ? "   §6Wersja" : "   §aVersion";
        String status = getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = getLang().equalsIgnoreCase("pl") ? "   §6Autor: §e" : "   §6Author: §e";
        String statusLabel = getLang().equalsIgnoreCase("pl") ? "   §6Status: " : "   §6Status: ";

        String review = getLang().equalsIgnoreCase("pl")
                ? "§bPodoba się plugin? Zostaw opinię na Discord'zie!"
                : "§bLike the plugin? Leave a review on Discord!";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6" + version + " §ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = getLang().equalsIgnoreCase("pl") ? "§eDziękujemy, że z nas korzystasz! Do zobaczenia!" : "§eThanks for choosing us! See you next time!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // METODY JĘZYKOWE PONIŻEJ

    public void sendVersionOk() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aAstraLogin jest aktualny §f(§ev" + plugin.getDescription().getVersion() + "§f)" : "§aAstraLogin is up to date §f(§ev" + plugin.getDescription().getVersion() + "§f)";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendVersionDevNotice(String latestStable) {
        String devTitle = getLang().equalsIgnoreCase("pl") ? "§bUżywasz wersji testowej: §fv" : "§bYou are using a Development version: §fv";
        String stableInfo = getLang().equalsIgnoreCase("pl") ? "§eNajnowsza wersja AstraLogin to: §fv" : "§eThe latest version of AstraLogin is: §fv";
        String warning = getLang().equalsIgnoreCase("pl") ? "§cUważaj na błędy, kod jest w fazie rozwoju!" : "§cWatch out for bugs, the code is in development!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(devTitle + plugin.getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage(stableInfo + latestStable);
        Bukkit.getConsoleSender().sendMessage(warning);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§cDostępna jest WIELKA aktualizacja AstraLogin: §fv"
                : "§cA MAJOR AstraLogin update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
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
        String title = getLang().equalsIgnoreCase("pl")
                ? "§eDostępna jest nowa aktualizacja AstraLogin: §fv"
                : "§eA new AstraLogin update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
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
        String title = getLang().equalsIgnoreCase("pl")
                ? "§bDostępna jest poprawka AstraLogin: §fv"
                : "§bAn AstraLogin bug fix is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
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
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§cNie udało się sprawdzić aktualizacji na Modrinth"
                : "§cFailed to check for updates on Modrinth";
        plugin.getLogger().warning(msg);
    }
}