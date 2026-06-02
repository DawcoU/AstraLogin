package pl.dawcou.astralogin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

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

    public void sendSpawnCreateError() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNie można utworzyć pliku spawns/locations.yml!" : "§cCould not create spawns/locations.yml file!";
        plugin.getLogger().severe(msg);
    }

    public void sendPlayerLocationReadError(String playerName) {
        String msg = getLang().equalsIgnoreCase("pl") ? "Błąd podczas odczytu pozycji dla " + playerName : "Error while reading location for " + playerName;
        plugin.getLogger().warning(msg);
    }

    public void sendSpawnSaveError() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNie można zapisać pliku spawns/locations.yml!" : "§cCould not save spawns/locations.yml file!";
        plugin.getLogger().severe(msg);
    }

    public void sendUpdateCheckError() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNie udało się sprawdzić aktualizacji na Modrinth" : "§cFailed to check for updates on Modrinth";
        plugin.getLogger().warning(msg);
    }

    public void sendNoIPSaved(CommandSender sender) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cTen gracz nie ma zapisanego adresu IP!" : "§cThis player does not have a saved IP address!";
        sender.sendMessage(PREFIX + " " + msg);
    }

    public void sendVersionOk(String version) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aAstraLogin jest aktualny §f(§ev" + version + "§f)" : "§aAstraLogin is up to date §f(§ev" + version + "§f)";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLoggerSuccess() {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aFiltr haseł został pomyślnie aktywowany" :
                "§aPassword filter has been successfully activated";
        plugin.getLogger().info(msg);
    }

    public void sendLoggerError(Exception e) {
        String errorMsg;

        if (getLang().equalsIgnoreCase("pl")) {
            errorMsg = "§4KRYTYCZNY BŁĄD: §cNie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli Błąd: " + e.getMessage();
        } else {
            errorMsg = "§4CRITICAL ERROR: §cFailed to activate log filter! Passwords may be visible in console Error: " + e.getMessage();
        }

        // Używamy severe, bo to poważna sprawa dotycząca bezpieczeństwa
        plugin.getLogger().severe(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(errorMsg)
        ));
    }

    public void sendLogSaveError(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zapisać logu bezpieczeństwa do pliku: §e" + fileName :
                "§cFailed to save security log to file: §e" + fileName;
        plugin.getLogger().severe(msg);
    }

    public void sendSessionSaveError(Exception e) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zapisać sesji do pliku! Błąd: " :
                "§cCould not save sessions to file! Error: ";
        plugin.getLogger().severe(msg + e.getMessage());
    }

    public void sendInvalidUUIDError(Exception e) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "Nie udało się usunąć sesji. Błędny format UUID! Błąd: " :
                "Failed to delete session. Incorrect UUID format! Error: ";

        plugin.getLogger().warning(msg + e.getMessage());
    }

    public void sendSessionsLoaded(int count) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§eWczytano §6" + count + " §eaktywnych sesji z pliku" :
                "§eLoaded §6" + count + " §eactive sessions from file";
        plugin.getLogger().info(msg);
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

    public void sendUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl") ? "§eDostępna jest nowa wersja AstraLogin: §fv" : "§eA new version of AstraLogin is available: §fv";
        String download = getLang().equalsIgnoreCase("pl") ? "§aPobierz: " : "§aDownload: ";
        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendDevNotice(String currentVersion, String latestStable) {
        String devTitle = getLang().equalsIgnoreCase("pl") ? "§bUżywasz wersji testowej: §f§nv" : "§bYou are using a Development version: §f§nv";
        String stableInfo = getLang().equalsIgnoreCase("pl") ? "§eNa Modrinth najnowsza stabilna to: §fv" : "§eThe latest stable on Modrinth is: §fv";
        String warning = getLang().equalsIgnoreCase("pl") ? "§bUważaj na błędy, kod jest w fazie rozwoju!" : "§bWatch out for bugs, the code is in development!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(devTitle + currentVersion);
        Bukkit.getConsoleSender().sendMessage(stableInfo + latestStable);
        Bukkit.getConsoleSender().sendMessage(warning);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
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
}