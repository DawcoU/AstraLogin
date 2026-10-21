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
        String versionLabel;
        String authorLabel;
        String playerCmds;

        // Zmienne dla komend i parametrów gracza
        String regCmd, regSyntax, regDesc;
        String loginCmd, loginSyntax, loginDesc;
        String logoutCmd, logoutDesc;
        String changePassCmd, changePassSyntax, changePassDesc;
        String pinCmd, pinSyntax, pinDesc;
        String forgotCmd, forgotSyntax, forgotDesc;
        String tfaCmd, tfaSyntax, tfaDesc;

        // Zmienne dla komend i parametrów admina
        String adminCmds;
        String accountCmd, accountSyntax, accountDesc;
        String listCmd, listDesc;
        String resetPassCmd, resetPassSyntax, resetPassDesc;
        String resetIpCmd, resetIpSyntax, resetIpDesc;
        String resetPinCmd, resetPinSyntax, resetPinDesc;
        String resetAccountCmd, resetAccountSyntax, resetAccountDesc;
        String moveCmd, moveSyntax, moveDesc;
        String spawnCmd, spawnSyntax, spawnDesc;
        String ipManagerCmd, ipManagerSyntax, ipManagerDesc;
        String ipTrustCmd, ipTrustSyntax, ipTrustDesc;
        String reloadCmd, reloadSyntax, reloadDesc;
        String updateCmd, updateSyntax, updateDesc;

        switch (lang) {
            case "pl":
                helpTitle = "AstraLogin System Pomocy";
                versionLabel = "Wersja:";
                authorLabel = "Autor:";
                playerCmds = "Komendy Gracza:";

                regCmd = "/rejestruj";
                regSyntax = "<hasło> <powtórz>";
                regDesc = "Rejestracja nowego konta";

                loginCmd = "/zaloguj";
                loginSyntax = "<hasło>";
                loginDesc = "Logowanie do gry";

                logoutCmd = "/wyloguj";
                logoutDesc = "Wylogowanie się z serwera";

                changePassCmd = "/zmienhaslo";
                changePassSyntax = "<stare> <nowe> <powtórz>";
                changePassDesc = "Zmiana obecnego hasła";

                pinCmd = "/pin";
                pinSyntax = "<ustaw> <PIN/Auto>";
                pinDesc = "Wygenerowanie lub ustawienie PIN-u";

                forgotCmd = "/zapomnialemhasla";
                forgotSyntax = "<PIN>";
                forgotDesc = "Reset hasła za pomocą PIN-u";

                tfaCmd = "/2fa";
                tfaSyntax = "<konfiguracja/usun/kod>";
                tfaDesc = "Zarządzanie i weryfikacja 2FA";

                adminCmds = "Komendy Administracji:";

                accountCmd = "/konto";
                accountSyntax = "<gracz>";
                accountDesc = "Zaawansowane statystyki konta";

                listCmd = "/listakont";
                listDesc = "Lista wszystkich kont AstraLogin";

                resetPassCmd = "/zresetujhaslo";
                resetPassSyntax = "<gracz>";
                resetPassDesc = "Usuwa hasło gracza";

                resetIpCmd = "/zresetujip";
                resetIpSyntax = "<gracz>";
                resetIpDesc = "Resetuje adres IP gracza";

                resetPinCmd = "/zresetujpin";
                resetPinSyntax = "<gracz>";
                resetPinDesc = "Resetuje PIN gracza";

                resetAccountCmd = "/zresetujkonto";
                resetAccountSyntax = "<gracz>";
                resetAccountDesc = "Czyszczenie wszystkich danych gracza";

                moveCmd = "/przenieskonto";
                moveSyntax = "<stary> <nowy>";
                moveDesc = "Przenoszenie danych na inne konto";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<ustawspawn/usunspawn> <typ>";
                spawnDesc = "Ustawianie i usuwanie spawnu";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/odbanuj/omijanie/usunomijanie> <gracz>";
                ipManagerDesc = "Zarządzanie blokadami IP";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/ustaw/zresetuj> <IP> <punkty>";
                ipTrustDesc = "Reputacja IP gracza";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";
                reloadDesc = "Przeładowanie konfiguracji pluginu";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                updateDesc = "Pobiera najnowszą wersję z Modrintha";
                break;

            case "de":
                helpTitle = "AstraLogin Hilfesystem";
                versionLabel = "Version:";
                authorLabel = "Autor:";
                playerCmds = "Spieler-Befehle:";

                regCmd = "/register";
                regSyntax = "<passwort> <wiederholen>";
                regDesc = "Registrieren eines neuen Kontos";

                loginCmd = "/login";
                loginSyntax = "<passwort>";
                loginDesc = "Auf dem Server anmelden";

                logoutCmd = "/logout";
                logoutDesc = "Vom Server abmelden";

                changePassCmd = "/changepassword";
                changePassSyntax = "<altes> <neues> <wiederholen>";
                changePassDesc = "Aktuelles Passwort ändern";

                pinCmd = "/pin";
                pinSyntax = "<setzen> <PIN/Auto>";
                pinDesc = "Konto-PIN festlegen oder generieren";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";
                forgotDesc = "Passwort mit PIN zurücksetzen";

                tfaCmd = "/2fa";
                tfaSyntax = "<einrichten/entfernen/code>";
                tfaDesc = "2FA verwalten und verifizieren";

                adminCmds = "Admin-Befehle:";

                accountCmd = "/account";
                accountSyntax = "<spieler>";
                accountDesc = "Erweiterte Kontostatistiken anzeigen";

                listCmd = "/accountslist";
                listDesc = "Alle AstraLogin-Konten anzeigen";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<spieler>";
                resetPassDesc = "Löscht das Passwort eines Spielers";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<spieler>";
                resetIpDesc = "Setzt die IP-Adresse des Spielers zurück";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<spieler>";
                resetPinDesc = "Setzt den PIN des Spielers zurück";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<spieler>";
                resetAccountDesc = "Löscht alle Spielerdaten";

                moveCmd = "/moveaccount";
                moveSyntax = "<alt> <neu>";
                moveDesc = "Überträgt Spielerdaten auf ein anderes Konto";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<setzen/entfernen> <typ>";
                spawnDesc = "Setzt und entfernt Login-Spawn";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/entbannen/bypass/unbypass> <spieler>";
                ipManagerDesc = "IP-Sperren und Bypasses verwalten";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/setzen/zurücksetzen> <IP> <punkte>";
                ipTrustDesc = "Zeigt und verwaltet die IP-Reputation";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";
                reloadDesc = "Lädt die Plugin-Konfiguration neu";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                updateDesc = "Lädt die neueste Version von Modrinth herunter";
                break;

            case "es":
                helpTitle = "AstraLogin Sistema de Ayuda";
                versionLabel = "Versión:";
                authorLabel = "Autor:";
                playerCmds = "Comandos de Jugador:";

                regCmd = "/register";
                regSyntax = "<contraseña> <repetir>";
                regDesc = "Registrar una nueva cuenta";

                loginCmd = "/login";
                loginSyntax = "<contraseña>";
                loginDesc = "Iniciar sesión en el servidor";

                logoutCmd = "/logout";
                logoutDesc = "Cerrar sesión en el servidor";

                changePassCmd = "/changepassword";
                changePassSyntax = "<anterior> <nueva> <repetir>";
                changePassDesc = "Cambiar tu contraseña actual";

                pinCmd = "/pin";
                pinSyntax = "<establecer> <PIN/Auto>";
                pinDesc = "Establecer o generar PIN de cuenta";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";
                forgotDesc = "Restablecer contraseña usando PIN";

                tfaCmd = "/2fa";
                tfaSyntax = "<configurar/eliminar/código>";
                tfaDesc = "Administrar y verificar 2FA";

                adminCmds = "Comandos de Administración:";

                accountCmd = "/account";
                accountSyntax = "<jugador>";
                accountDesc = "Ver estadísticas avanzadas de la cuenta";

                listCmd = "/accountslist";
                listDesc = "Ver todas las cuentas de AstraLogin";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<jugador>";
                resetPassDesc = "Elimina la contraseña de un jugador";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<jugador>";
                resetIpDesc = "Restablece la dirección IP del jugador";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<jugador>";
                resetPinDesc = "Restablece el PIN del jugador";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<jugador>";
                resetAccountDesc = "Borra todos los datos del jugador";

                moveCmd = "/moveaccount";
                moveSyntax = "<anterior> <nuevo>";
                moveDesc = "Transfiere datos del jugador a otra cuenta";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<establecer/eliminar> <tipo>";
                spawnDesc = "Establece y elimina el spawn de inicio de sesión";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/desbanear/excepción/quitarexcepción> <jugador>";
                ipManagerDesc = "Gestionar bloqueos de IP y excepciones";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/establecer/restablecer> <IP> <puntuación>";
                ipTrustDesc = "Muestra y gestiona la reputación de IP";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";
                reloadDesc = "Recarga la configuración del plugin";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                updateDesc = "Descarga la última versión desde Modrinth";
                break;

            default:
                helpTitle = "AstraLogin Help System";
                versionLabel = "Version:";
                authorLabel = "Author:";
                playerCmds = "Player Commands:";

                regCmd = "/register";
                regSyntax = "<pass> <repeat>";
                regDesc = "Register a new account";

                loginCmd = "/login";
                loginSyntax = "<pass>";
                loginDesc = "Log into the server";

                logoutCmd = "/logout";
                logoutDesc = "Log out of the server";

                changePassCmd = "/changepassword";
                changePassSyntax = "<old> <new> <repeat>";
                changePassDesc = "Change your current password";

                pinCmd = "/pin";
                pinSyntax = "<set> <PIN/Auto>";
                pinDesc = "Set or generate account PIN";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";
                forgotDesc = "Reset password using PIN";

                tfaCmd = "/2fa";
                tfaSyntax = "<setup/unsetup/code>";
                tfaDesc = "Manage and verify 2FA";

                adminCmds = "Admin Commands:";

                accountCmd = "/account";
                accountSyntax = "<player>";
                accountDesc = "View advanced account stats";

                listCmd = "/accountslist";
                listDesc = "View all AstraLogin accounts";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<player>";
                resetPassDesc = "Deletes a player's password";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<player>";
                resetIpDesc = "Resets the player's IP address";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<player>";
                resetPinDesc = "Resets the player's PIN";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<player>";
                resetAccountDesc = "Wipes all player data";

                moveCmd = "/moveaccount";
                moveSyntax = "<old> <new>";
                moveDesc = "Transfers player data to another account";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<setspawn/delspawn> <type>";
                spawnDesc = "Sets and removes login spawn";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/unban/bypass/unbypass> <player>";
                ipManagerDesc = "Manage IP bans and bypass";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/set/reset> <IP> <score>";
                ipTrustDesc = "Shows and manages IP reputation";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";
                reloadDesc = "Reloads plugin configuration";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                updateDesc = "Downloads the latest version from Modrinth";
                break;
        }

        sender.sendMessage(p + "§b§l=== " + helpTitle + " ===");
        sender.sendMessage(p + "§7" + versionLabel + " §f" + plugin.getDescription().getVersion() + " §7| " + authorLabel + " §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + playerCmds);
        sender.sendMessage(p + " §f" + regCmd + " " + regSyntax + " §7- " + regDesc);
        sender.sendMessage(p + " §f" + loginCmd + " " + loginSyntax + " §7- " + loginDesc);
        sender.sendMessage(p + " §f" + logoutCmd + " §7- " + logoutDesc);
        sender.sendMessage(p + " §f" + changePassCmd + " " + changePassSyntax + " §7- " + changePassDesc);
        sender.sendMessage(p + " §f" + pinCmd + " " + pinSyntax + " §7- " + pinDesc);
        sender.sendMessage(p + " §f" + forgotCmd + " " + forgotSyntax + " §7- " + forgotDesc);
        sender.sendMessage(p + " §f" + tfaCmd + " " + tfaSyntax + " §7- " + tfaDesc);

        if (sender.hasPermission("astralogin.account") || sender.hasPermission("astralogin.reload") || sender.hasPermission("astralogin.update")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + adminCmds);
            sender.sendMessage(p + " §f" + accountCmd + " " + accountSyntax + " §7- " + accountDesc);
            sender.sendMessage(p + " §f" + listCmd + " §7- " + listDesc);
            sender.sendMessage(p + " §f" + resetPassCmd + " " + resetPassSyntax + " §7- " + resetPassDesc);
            sender.sendMessage(p + " §f" + resetIpCmd + " " + resetIpSyntax + " §7- " + resetIpDesc);
            sender.sendMessage(p + " §f" + resetPinCmd + " " + resetPinSyntax + " §7- " + resetPinDesc);
            sender.sendMessage(p + " §f" + resetAccountCmd + " " + resetAccountSyntax + " §7- " + resetAccountDesc);
            sender.sendMessage(p + " §f" + moveCmd + " " + moveSyntax + " §7- " + moveDesc);
            sender.sendMessage(p + " §f" + spawnCmd + " " + spawnSyntax + " §7- " + spawnDesc);
            sender.sendMessage(p + " §f" + ipManagerCmd + " " + ipManagerSyntax + " §7- " + ipManagerDesc);
            sender.sendMessage(p + " §f" + ipTrustCmd + " " + ipTrustSyntax + " §7- " + ipTrustDesc);
            sender.sendMessage(p + " §f" + reloadCmd + " " + reloadSyntax + " §7- " + reloadDesc);
            sender.sendMessage(p + " §f" + updateCmd + " " + updateSyntax + " §7- " + updateDesc);
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
                msg = "§aPomyślnie utworzono kopię zapasową: §e";
                break;
            case "de":
                msg = "§aBackup erfolgreich erstellt: §e";
                break;
            case "es":
                msg = "§aCopia de seguridad creada con éxito: §e";
                break;
            default:
                msg = "§aBackup successfully created: §e";
                break;
        }
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    public void sendAutomaticBackupSave(String fileName) {
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

    public void sendExperimentalNotice(CommandSender target, String latestStable) {
        String lang = getLang().toLowerCase();
        String devTitle;
        String warning;
        String stableInfo;
        String download;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz eksperymentalnej wersji: §fv";
                warning = "§cUżywaj tylko dla testów! Kod jest w fazie rozwoju!";
                stableInfo = "§eNajnowsza stabilna wersja AstraLogin: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                devTitle = "§bDu verwendest eine experimentelle Version: §fv";
                warning = "§cNur für Testzwecke verwenden! Der Code befindet sich in der Entwicklung!";
                stableInfo = "§eNeueste stabile AstraLogin-Version: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                devTitle = "§bEstás usando una versión experimental: §fv";
                warning = "§c¡Usa solo para pruebas! ¡El código está en desarrollo!";
                stableInfo = "§eÚltima versión estable de AstraLogin: §fv";
                download = "§aDescargar: ";
                break;
            default:
                devTitle = "§bYou are using an experimental version: §fv";
                warning = "§cUse only for testing! The code is in development!";
                stableInfo = "§eLatest stable AstraLogin version: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage(stableInfo + latestStable);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + latestStable);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionPreReleaseNotice(CommandSender target, String version) {
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