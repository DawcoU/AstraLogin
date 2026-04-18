package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class NoticeManager {

    private final AstraLogin plugin;
    private final String PREFIX;
    private final String PREFIX2;

    public NoticeManager(AstraLogin plugin) {
        this.plugin = plugin;
        // Pobieramy prefixy z głównej klasy (upewnij się, że są tam publiczne)
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

    public void sendInvalidSessionFormatNotice() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNieprawidłowy format session-time! Używam domyślnych 5 minut." : "§cInvalid session-time format! Using default 5 minutes.";
        plugin.getLogger().warning(msg);
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

    public void sendLangUpdateSuccess(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aZaktualizowano brakujące linijki w pliku" :
                "§aSuccessfully updated missing lines in file";
        plugin.getLogger().info(msg + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie można było zaktualizować pliku językowego (" + fileName + "): " :
                "§cCould not update language file (" + fileName + "): ";
        plugin.getLogger().severe(msg + error);
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
        String devTitle = getLang().equalsIgnoreCase("pl") ? "§bUżywasz wersji testowej (Development): §f§nv" : "§bYou are using a Development version: §f§nv";
        String stableInfo = getLang().equalsIgnoreCase("pl") ? "§eNa Modrinth najnowsza stabilna to: §fv" : "§eThe latest stable on Modrinth is: §fv";
        String warning = getLang().equalsIgnoreCase("pl") ? "§bUważaj na błędy, kod jest w fazie rozwoju!" : "§bWatch out for bugs, the code is in development!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(devTitle + currentVersion);
        Bukkit.getConsoleSender().sendMessage(stableInfo + latestStable);
        Bukkit.getConsoleSender().sendMessage(warning);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = getLang().equalsIgnoreCase("pl") ? "§6Wersja" : "§aVersion";
        String status = getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = getLang().equalsIgnoreCase("pl") ? "§6   Autor: §e" : "§6   Author: §e";
        String statusLabel = getLang().equalsIgnoreCase("pl") ? "§6   Status: " : "§6   Status: ";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   " + version + " §ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = getLang().equalsIgnoreCase("pl") ? "§eDo zobaczenia! :D" : "§eSee you! :D";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }
}