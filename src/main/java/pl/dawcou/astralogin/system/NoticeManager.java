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

    // --- METODY POWIADOMIEŃ KOMEND I POMOCY ---

    public void sendHelp(CommandSender sender) {
        String p = getPrefix(sender) + " ";
        String lang = getLang().toLowerCase();

        String helpTitle;
        String playerCmds;
        String regDesc;
        String loginDesc;
        String logoutDesc;
        String changePassDesc;
        String pinDesc;
        String forgotDesc;
        String tfaDesc;
        String adminCmds;
        String accountDesc;
        String listDesc;
        String resetPassDesc;
        String resetIpDesc;
        String resetPinDesc;
        String resetAccountDesc;
        String moveDesc;
        String spawnDesc;
        String ipManagerDesc;
        String ipTrustDesc;
        String reloadDesc;

        switch (lang) {
            case "pl":
                helpTitle = "AstraLogin System Pomocy";
                playerCmds = "Komendy Gracza:";
                regDesc = "Rejestracja nowego konta";
                loginDesc = "Logowanie do gry";
                logoutDesc = "Wylogowanie się z serwera";
                changePassDesc = "Zmiana obecnego hasła";
                pinDesc = "Wygenerowanie lub ustawienie PIN-u";
                forgotDesc = "Reset hasła za pomocą PIN-u";
                tfaDesc = "Zarządzanie i weryfikacja 2FA";
                adminCmds = "Komendy Administracji:";
                accountDesc = "Zaawansowane statystyki konta";
                listDesc = "Lista wszystkich kont AstraLogin";
                resetPassDesc = "Usuwa hasło gracza";
                resetIpDesc = "Resetuje adres IP gracza";
                resetPinDesc = "Resetuje PIN gracza";
                resetAccountDesc = "Czyszczenie wszystkich danych gracza";
                moveDesc = "Przenoszenie danych na inne konto";
                spawnDesc = "Ustawianie i usuwanie spawnu";
                ipManagerDesc = "Zarządzanie blokadami IP";
                ipTrustDesc = "Reputacja IP gracza";
                reloadDesc = "Przeładowanie konfiguracji pluginu";
                break;
            case "de":
                helpTitle = "AstraLogin Hilfesystem";
                playerCmds = "Spieler-Befehle:";
                regDesc = "Registrieren eines neuen Kontos";
                loginDesc = "Auf dem Server anmelden";
                logoutDesc = "Vom Server abmelden";
                changePassDesc = "Aktuelles Passwort ändern";
                pinDesc = "Konto-PIN festlegen oder generieren";
                forgotDesc = "Passwort mit PIN zurücksetzen";
                tfaDesc = "2FA verwalten und verifizieren";
                adminCmds = "Admin-Befehle:";
                accountDesc = "Erweiterte Kontostatistiken anzeigen";
                listDesc = "Alle AstraLogin-Konten anzeigen";
                resetPassDesc = "Löscht das Passwort eines Spielers";
                resetIpDesc = "Setzt die IP-Adresse des Spielers zurück";
                resetPinDesc = "Setzt den PIN des Spielers zurück";
                resetAccountDesc = "Löscht alle Spielerdaten";
                moveDesc = "Überträgt Spielerdaten auf ein anderes Konto";
                spawnDesc = "Setzt und entfernt Login-Spawn";
                ipManagerDesc = "IP-Sperren und Bypasses verwalten";
                ipTrustDesc = "Zeigt und verwaltet die IP-Reputation";
                reloadDesc = "Lädt die Plugin-Konfiguration neu";
                break;
            case "es":
                helpTitle = "AstraLogin Sistema de Ayuda";
                playerCmds = "Comandos de Jugador:";
                regDesc = "Registrar una nueva cuenta";
                loginDesc = "Iniciar sesión en el servidor";
                logoutDesc = "Cerrar sesión en el servidor";
                changePassDesc = "Cambiar tu contraseña actual";
                pinDesc = "Establecer o generar PIN de cuenta";
                forgotDesc = "Restablecer contraseña usando PIN";
                tfaDesc = "Administrar y verificar 2FA";
                adminCmds = "Comandos de Administración:";
                accountDesc = "Ver estadísticas avanzadas de la cuenta";
                listDesc = "Ver todas las cuentas de AstraLogin";
                resetPassDesc = "Elimina la contraseña de un jugador";
                resetIpDesc = "Restablece la dirección IP del jugador";
                resetPinDesc = "Restablece el PIN del jugador";
                resetAccountDesc = "Borra todos los datos del jugador";
                moveDesc = "Transfiere datos del jugador a otra cuenta";
                spawnDesc = "Establece y elimina el spawn de inicio de sesión";
                ipManagerDesc = "Gestionar bloqueos de IP y excepciones";
                ipTrustDesc = "Muestra y gestiona la reputación de IP";
                reloadDesc = "Recarga la configuración del plugin";
                break;
            default:
                helpTitle = "AstraLogin Help System";
                playerCmds = "Player Commands:";
                regDesc = "Register a new account";
                loginDesc = "Log into the server";
                logoutDesc = "Log out of the server";
                changePassDesc = "Change your current password";
                pinDesc = "Set or generate account PIN";
                forgotDesc = "Reset password using PIN";
                tfaDesc = "Manage and verify 2FA";
                adminCmds = "Admin Commands:";
                accountDesc = "View advanced account stats";
                listDesc = "View all AstraLogin accounts";
                resetPassDesc = "Deletes a player's password";
                resetIpDesc = "Resets the player's IP address";
                resetPinDesc = "Resets the player's PIN";
                resetAccountDesc = "Wipes all player data";
                moveDesc = "Transfers player data to another account";
                spawnDesc = "Sets and removes login spawn";
                ipManagerDesc = "Manage IP bans and bypass";
                ipTrustDesc = "Shows and manages IP reputation";
                reloadDesc = "Reloads plugin configuration";
                break;
        }

        sender.sendMessage(p + "§b§l=== " + helpTitle + " ===");
        sender.sendMessage(p + "§7Wersja: §f" + plugin.getDescription().getVersion() + " §7| Autor: §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + playerCmds);
        sender.sendMessage(p + " §f/register <pass> <repeat> §7- " + regDesc);
        sender.sendMessage(p + " §f/login <pass> §7- " + loginDesc);
        sender.sendMessage(p + " §f/logout §7- " + logoutDesc);
        sender.sendMessage(p + " §f/changepassword <old> <new> <repeat> §7- " + changePassDesc);
        sender.sendMessage(p + " §f/pin <set> <PIN/Auto> §7- " + pinDesc);
        sender.sendMessage(p + " §f/forgotpass <PIN> §7- " + forgotDesc);
        sender.sendMessage(p + " §f/2fa <setup/unsetup/code> §7- " + tfaDesc);

        if (sender.hasPermission("astralogin.account") || sender.hasPermission("astralogin.reload")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + adminCmds);
            sender.sendMessage(p + " §f/account <player> §7- " + accountDesc);
            sender.sendMessage(p + " §f/accountslist §7- " + listDesc);
            sender.sendMessage(p + " §f/resetpassword <player> §7- " + resetPassDesc);
            sender.sendMessage(p + " §f/resetip <player> §7- " + resetIpDesc);
            sender.sendMessage(p + " §f/resetpin <player> §7- " + resetPinDesc);
            sender.sendMessage(p + " §f/resetaccount <player> §7- " + resetAccountDesc);
            sender.sendMessage(p + " §f/moveaccount <old> <new> §7- " + moveDesc);
            sender.sendMessage(p + " §f/loginspawn <setspawn/delspawn> <type> §7- " + spawnDesc);
            sender.sendMessage(p + " §f/ipmanager <info/unban/bypass/unbypass> <player> §7- " + ipManagerDesc);
            sender.sendMessage(p + " §f/iptrust <info/set/reset> <IP> <score> §7- " + ipTrustDesc);
            sender.sendMessage(p + " §f/astralogin reload §7- " + reloadDesc);
        }

        sender.sendMessage(p + "§b§l=================================");
    }

    // --- METODY POWIADOMIEŃ PLIKÓW I KONFIGURACJI ---

    public void sendConfigUpdateNotice() {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aPomyślnie dopisano brakujące klucze do pliku konfiguracyjnego";
                break;
            case "de":
                msg = "§aFehlende Schlüssel wurden erfolgreich zur Konfigurationsdatei hinzugefügt";
                break;
            case "es":
                msg = "§aLas claves faltantes se agregaron con éxito al archivo de configuración";
                break;
            default:
                msg = "§aMissing keys were successfully added to the configuration file";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Błąd podczas zapisu pliku konfiguracyjnego. Błąd:";
                break;
            case "de":
                msg = "Fehler beim Speichern der Konfigurationsdatei. Fehler:";
                break;
            case "es":
                msg = "Error al guardar el archivo de configuración. Error:";
                break;
            default:
                msg = "Error while saving config. Error:";
                break;
        }
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aDodano brakujące klucze w pliku językowym:";
                break;
            case "de":
                msg = "§aFehlende Schlüssel in der Sprachdatei hinzugefügt:";
                break;
            case "es":
                msg = "§aSe agregaron claves faltantes en el archivo de idioma:";
                break;
            default:
                msg = "§aAdded missing keys in language file:";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Nie udało się zaktualizować pliku językowego (" + fileName + "). Błąd:";
                break;
            case "de":
                msg = "Sprachdatei konnte nicht aktualisiert werden (" + fileName + "). Fehler:";
                break;
            case "es":
                msg = "Error al actualizar el archivo de idioma (" + fileName + "). Error:";
                break;
            default:
                msg = "Failed to update language file (" + fileName + "). Error:";
                break;
        }
        plugin.getLogger().severe(msg + " " + error);
    }

    // --- METODY POWIADOMIEŃ BŁĘDÓW I SYSTEMOWYCH ---

    public void sendPlayerLocationReadError(String playerName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Błąd podczas odczytu pozycji dla " + playerName;
                break;
            case "de":
                msg = "Fehler beim Lesen des Standorts für " + playerName;
                break;
            case "es":
                msg = "Error al leer la ubicación de " + playerName;
                break;
            default:
                msg = "Error while reading location for " + playerName;
                break;
        }
        plugin.getLogger().severe(msg);
    }

    public void sendLoggerError(Exception e) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "KRYTYCZNY BŁĄD: Nie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli. Błąd: " + e.getMessage();
                break;
            case "de":
                msg = "KRITISCHER FEHLER: Log-Filter konnte nicht aktiviert werden! Passwörter könnten in der Konsole sichtbar sein. Fehler: " + e.getMessage();
                break;
            case "es":
                msg = "ERROR CRÍTICO: ¡No se pudo activar el filtro de registros! Las contraseñas pueden ser visibles en la consola. Error: " + e.getMessage();
                break;
            default:
                msg = "CRITICAL ERROR: Failed to activate log filter! Passwords may be visible in console. Error: " + e.getMessage();
                break;
        }
        plugin.getLogger().severe(msg);
    }

    public void sendPremiumCheckError(String player, Exception e) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Nie udało się zweryfikować gracza " + player + ". Błąd: " + e.getMessage();
                break;
            case "de":
                msg = "Spieler " + player + " konnte nicht verifiziert werden. Fehler: " + e.getMessage();
                break;
            case "es":
                msg = "No se pudo verificar al jugador " + player + ". Error: " + e.getMessage();
                break;
            default:
                msg = "Failed to verify player " + player + ". Error: " + e.getMessage();
                break;
        }
        plugin.getLogger().severe(msg);
    }

    public void sendLogSaveError(String fileName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Nie udało się zapisać logu bezpieczeństwa do pliku: " + fileName;
                break;
            case "de":
                msg = "Sicherheitslog konnte nicht in der Datei gespeichert werden: " + fileName;
                break;
            case "es":
                msg = "No se pudo guardar el registro de seguridad en el archivo: " + fileName;
                break;
            default:
                msg = "Failed to save security log to file: " + fileName;
                break;
        }
        plugin.getLogger().severe(msg);
    }

    public void sendBackupSaveError(String error) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Nie udało się utworzyć kopii zapasowej. Błąd:";
                break;
            case "de":
                msg = "Sicherung konnte nicht erstellt werden. Fehler:";
                break;
            case "es":
                msg = "Error al crear la copia de seguridad. Error:";
                break;
            default:
                msg = "Backup failed. Error:";
                break;
        }
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendBackupSave(String fileName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aPomyślnie utworzono automatyczną kopię zapasową: §e";
                break;
            case "de":
                msg = "§aAutomatisches Backup erfolgreich erstellt: §e";
                break;
            case "es":
                msg = "§aCopia de seguridad automática creada con éxito: §e";
                break;
            default:
                msg = "§aAutomatic backup successfully created: §e";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    public void sendAutomaticBackupSave(String fileName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aPomyślnie utworzono kopię zapasową danych przed migracją: §e";
                break;
            case "de":
                msg = "§aDatensicherung vor der Migration erfolgreich erstellt: §e";
                break;
            case "es":
                msg = "§aCopia de seguridad creada con éxito antes de la migración: §e";
                break;
            default:
                msg = "§aBackup of data successfully created before migration: §e";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    // --- MIGRACJA I SESJE ---

    public void sendMigrationNotice(String oldName, String newName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§6Migracja: §f" + oldName + " §7-> §f" + newName + "...";
                break;
            case "de":
                msg = "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
                break;
            case "es":
                msg = "§6Migración: §f" + oldName + " §7-> §f" + newName + "...";
                break;
            default:
                msg = "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSuccessMigrationNotice(String name) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aPlik/Sekcja §f" + name + " §azostała pomyślnie przeniesiona!";
                break;
            case "de":
                msg = "§aDatei/Abschnitt §f" + name + " §awurde erfolgreich migriert!";
                break;
            case "es":
                msg = "§a¡Archivo/Sección §f" + name + " §amigrado con éxito!";
                break;
            default:
                msg = "§aFile/Section §f" + name + " §ahas been successfully migrated!";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendErrorMigrationNotice(String action) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§cBłąd podczas migracji: §f" + action;
                break;
            case "de":
                msg = "§cFehler bei der Migration: §f" + action;
                break;
            case "es":
                msg = "§cError durante la migración: §f" + action;
                break;
            default:
                msg = "§cError during migration: §f" + action;
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSessionsLoaded(int count) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§eWczytano §6" + count + " §eaktywnych sesji z pliku";
                break;
            case "de":
                msg = "§e" + count + " §eaktive Sitzungen aus Datei geladen";
                break;
            case "es":
                msg = "§eSe cargaron §6" + count + " §ecesiones activas desde el archivo";
                break;
            default:
                msg = "§eLoaded §6" + count + " §eactive sessions from file";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    // --- LOGO STARTOWE I KOŃCOWE ---

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String lang = getLang().toLowerCase();

        String versionLabel;
        String status;
        String authorLabel;
        String statusLabel;
        String review;

        switch (lang) {
            case "pl":
                versionLabel = "   §6Wersja: ";
                status = "§aWłączony";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Status: ";
                review = "§bPodoba Ci się plugin? Zostaw opinię na Discordzie!";
                break;
            case "de":
                versionLabel = "   §6Version: ";
                status = "§aAktiviert";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Status: ";
                review = "§bGefällt dir das Plugin? Hinterlasse eine Bewertung auf Discord!";
                break;
            case "es":
                versionLabel = "   §6Versión: ";
                status = "§aActivado";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Estado: ";
                review = "§b¿Te gusta el plugin? ¡Deja una reseña en Discord!";
                break;
            default:
                versionLabel = "   §6Version: ";
                status = "§aEnabled";
                authorLabel = "   §6Author: §e";
                statusLabel = "   §6Status: ";
                review = "§bLike the plugin? Leave a review on Discord!";
                break;
        }

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage(versionLabel + "§ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(authorLabel + plugin.getAuthor());
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String lang = getLang().toLowerCase();
        String status;
        String farewell;

        switch (lang) {
            case "pl":
                status = "§cWyłączony";
                farewell = "§eDziękujemy, że z nas korzystasz! Do zobaczenia!";
                break;
            case "de":
                status = "§cDeaktiviert";
                farewell = "§eVielen Dank für die Nutzung! Bis zum nächsten Mal!";
                break;
            case "es":
                status = "§cDesactivado";
                farewell = "§e¡Gracias por elegirnos! ¡Hasta la próxima!";
                break;
            default:
                status = "§cDisabled";
                farewell = "§eThanks for choosing us! See you next time!";
                break;
        }

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // --- METODY POWIADOMIEŃ WERSJI I AKTUALIZACJI ---

    public void sendVersionOk(CommandSender target) {
        String prefix = getPrefix(target);
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "§aAstraLogin jest aktualny §f(§ev" + plugin.getDescription().getVersion() + "§f)";
                break;
            case "de":
                msg = "§aAstraLogin ist auf dem neuesten Stand §f(§ev" + plugin.getDescription().getVersion() + "§f)";
                break;
            case "es":
                msg = "§aAstraLogin está actualizado §f(§ev" + plugin.getDescription().getVersion() + "§f)";
                break;
            default:
                msg = "§aAstraLogin is up to date §f(§ev" + plugin.getDescription().getVersion() + "§f)";
                break;
        }

        target.sendMessage(prefix + " " + msg);
    }

    public void sendExperimentalNotice(CommandSender target) {
        String lang = getLang().toLowerCase();
        String devTitle;
        String warning;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz eksperymentalnej wersji: §fv";
                warning = "§cUżywaj tylko dla testów! Kod jest w fazie rozwoju!";
                break;
            case "de":
                devTitle = "§bDu verwendest eine experimentelle Version: §fv";
                warning = "§cNur für Testzwecke verwenden! Der Code befindet sich in der Entwicklung!";
                break;
            case "es":
                devTitle = "§bEstás usando una versión experimental: §fv";
                warning = "§c¡Usa solo para pruebas! ¡El código está en desarrollo!";
                break;
            default:
                devTitle = "§bYou are using an experimental version: §fv";
                warning = "§cUse only for testing! The code is in development!";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPreReleaseNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String info;
        String download;

        switch (lang) {
            case "pl":
                title = "§6[Pre-Release] §eDostępna jest wersja testowa: §b" + version;
                info = "§cUwaga: Wersja wyłącznie do celów testowych! Może zawierać błędy.";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§6[Pre-Release] §eTestversion verfügbar: §b" + version;
                info = "§cHinweis: Nur für Testzwecke! Kann Fehler enthalten.";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§6[Pre-Release] §eVersión de prueba disponible: §b" + version;
                info = "§cAviso: ¡Solo para fines de prueba! Puede contener errores.";
                download = "§aDescargar: ";
                break;
            default:
                title = "§6[Pre-Release] §eTest version available: §b" + version;
                info = "§cNotice: For testing purposes only! May contain bugs.";
                download = "§aDownload: ";
                break;
        }

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
        String lang = getLang().toLowerCase();
        String devTitle;
        String stableInfo;
        String warning;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz nowszej wersji niepublicznej: §fv";
                stableInfo = "§eNajnowsza publiczna wersja AstraLogin to: §fv";
                warning = "§cUważaj na błędy, kod jest w fazie rozwoju!";
                break;
            case "de":
                devTitle = "§bDu verwendest eine neuere, nicht öffentliche Version: §fv";
                stableInfo = "§eDie neueste öffentliche Version von AstraLogin ist: §fv";
                warning = "§cAchte auf Fehler, der Code befindet sich in der Entwicklung!";
                break;
            case "es":
                devTitle = "§bEstás usando una versión no pública más reciente: §fv";
                stableInfo = "§aLa última versión pública de AstraLogin es: §fv";
                warning = "§c¡Cuidado con los errores, el código está en desarrollo!";
                break;
            default:
                devTitle = "§bYou are using a newer, non-public version: §fv";
                stableInfo = "§eThe latest public version of AstraLogin is: §fv";
                warning = "§cWatch out for bugs, the code is in development!";
                break;
        }

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
        String lang = getLang().toLowerCase();
        String title;
        String download;

        switch (lang) {
            case "pl":
                title = "§cDostępna jest WIELKA aktualizacja AstraLogin: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§cEin GROSSES AstraLogin-Update ist verfügbar: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§cUna actualización MAYOR de AstraLogin está disponible: §fv";
                download = "§aDescargar: ";
                break;
            default:
                title = "§cA MAJOR AstraLogin update is available: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMinorUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download;

        switch (lang) {
            case "pl":
                title = "§eDostępna jest nowa aktualizacja AstraLogin: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§eEin neues AstraLogin-Update ist verfügbar: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§eUna nueva actualización de AstraLogin está disponible: §fv";
                download = "§aDescargar: ";
                break;
            default:
                title = "§eA new AstraLogin update is available: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPatchUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download;

        switch (lang) {
            case "pl":
                title = "§bDostępna jest mała aktualizacja AstraLogin: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§bEin kleineres AstraLogin-Update ist verfügbar: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§bUna pequeña actualización de AstraLogin está disponible: §fv";
                download = "§aDescargar: ";
                break;
            default:
                title = "§bA minor AstraLogin update is available: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "Nie udało się sprawdzić aktualizacji";
                break;
            case "de":
                msg = "Fehler beim Überprüfen auf Updates";
                break;
            case "es":
                msg = "Error al buscar actualizaciones";
                break;
            default:
                msg = "Failed to check for updates";
                break;
        }
        plugin.getLogger().warning(msg);
    }

    // --- EVENTY SEZONOWE ---

    public void sendSeasonalEventActivated(String eventName) {
        String msg;
        switch (getLang().toLowerCase()) {
            case "pl":
                msg = "[SeasonalEvents] Aktywowano wydarzenie sezonowe: " + eventName;
                break;
            case "de":
                msg = "[SeasonalEvents] Saisonaler Event aktiviert: " + eventName;
                break;
            case "es":
                msg = "[SeasonalEvents] Evento estacional activado: " + eventName;
                break;
            default:
                msg = "[SeasonalEvents] Activated seasonal event: " + eventName;
                break;
        }
        plugin.getLogger().info(msg);
    }
}