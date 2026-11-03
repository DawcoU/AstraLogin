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
        String regCmd, regSyntax;
        String loginCmd, loginSyntax;
        String logoutCmd;
        String changePassCmd, changePassSyntax;
        String pinCmd, pinSyntax;
        String forgotCmd, forgotSyntax;
        String tfaCmd, tfaSyntax;

        // Zmienne dla komend i parametrów admina
        String adminCmds;
        String accountCmd, accountSyntax;
        String listCmd;
        String resetPassCmd, resetPassSyntax;
        String resetIpCmd, resetIpSyntax;
        String resetPinCmd, resetPinSyntax;
        String resetAccountCmd, resetAccountSyntax;
        String moveCmd, moveSyntax;
        String spawnCmd, spawnSyntax;
        String ipManagerCmd, ipManagerSyntax;
        String ipTrustCmd, ipTrustSyntax;
        String reloadCmd, reloadSyntax;
        String updateCmd, updateSyntax;

        switch (lang) {
            case "pl":
                helpTitle = "AstraLogin System Pomocy";
                versionLabel = "Wersja:";
                authorLabel = "Autor:";
                playerCmds = "Komendy Gracza:";

                regCmd = "/register";
                regSyntax = "<pass> <repeat>";

                loginCmd = "/login";
                loginSyntax = "<pass>";

                logoutCmd = "/logout";

                changePassCmd = "/changepassword";
                changePassSyntax = "<old> <new> <repeat>";

                pinCmd = "/pin";
                pinSyntax = "<set> <PIN/Auto>";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";

                tfaCmd = "/2fa";
                tfaSyntax = "<setup/unsetup/code>";

                adminCmds = "Komendy Administracji:";

                accountCmd = "/account";
                accountSyntax = "<player>";

                listCmd = "/accountslist";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<player>";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<player>";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<player>";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<player>";

                moveCmd = "/moveaccount";
                moveSyntax = "<old> <new>";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<setspawn/delspawn> <type>";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/unban/bypass/unbypass> <player>";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/set/reset> <IP> <score>";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                break;

            case "de":
                helpTitle = "AstraLogin Hilfesystem";
                versionLabel = "Version:";
                authorLabel = "Autor:";
                playerCmds = "Spieler-Befehle:";

                regCmd = "/register";
                regSyntax = "<passwort> <wiederholen>";

                loginCmd = "/login";
                loginSyntax = "<passwort>";

                logoutCmd = "/logout";

                changePassCmd = "/changepassword";
                changePassSyntax = "<altes> <neues> <wiederholen>";

                pinCmd = "/pin";
                pinSyntax = "<setzen> <PIN/Auto>";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";

                tfaCmd = "/2fa";
                tfaSyntax = "<einrichten/entfernen/code>";

                adminCmds = "Admin-Befehle:";

                accountCmd = "/account";
                accountSyntax = "<spieler>";

                listCmd = "/accountslist";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<spieler>";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<spieler>";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<spieler>";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<spieler>";

                moveCmd = "/moveaccount";
                moveSyntax = "<alt> <neu>";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<setzen/entfernen> <typ>";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/entbannen/umgehung/unumgehung> <spieler>";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/setzen/zurücksetzen> <IP> <punkte>";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                break;

            case "es":
                helpTitle = "AstraLogin Sistema de Ayuda";
                versionLabel = "Versión:";
                authorLabel = "Autor:";
                playerCmds = "Comandos de Jugador:";

                regCmd = "/register";
                regSyntax = "<contraseña> <repetir>";

                loginCmd = "/login";
                loginSyntax = "<contraseña>";

                logoutCmd = "/logout";

                changePassCmd = "/changepassword";
                changePassSyntax = "<anterior> <nueva> <repetir>";

                pinCmd = "/pin";
                pinSyntax = "<establecer> <PIN/Auto>";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";

                tfaCmd = "/2fa";
                tfaSyntax = "<configurar/eliminar/código>";

                adminCmds = "Comandos de Administración:";

                accountCmd = "/account";
                accountSyntax = "<jugador>";

                listCmd = "/accountslist";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<jugador>";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<jugador>";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<jugador>";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<jugador>";

                moveCmd = "/moveaccount";
                moveSyntax = "<anterior> <nuevo>";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<establecer/eliminar> <tipo>";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/desbanear/excepción/quitarexcepción> <jugador>";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/establecer/restablecer> <IP> <puntuación>";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                break;

            default:
                helpTitle = "AstraLogin Help System";
                versionLabel = "Version:";
                authorLabel = "Author:";
                playerCmds = "Player Commands:";

                regCmd = "/register";
                regSyntax = "<pass> <repeat>";

                loginCmd = "/login";
                loginSyntax = "<pass>";

                logoutCmd = "/logout";

                changePassCmd = "/changepassword";
                changePassSyntax = "<old> <new> <repeat>";

                pinCmd = "/pin";
                pinSyntax = "<set> <PIN/Auto>";

                forgotCmd = "/forgotpass";
                forgotSyntax = "<PIN>";

                tfaCmd = "/2fa";
                tfaSyntax = "<setup/unsetup/code>";

                adminCmds = "Admin Commands:";

                accountCmd = "/account";
                accountSyntax = "<player>";

                listCmd = "/accountslist";

                resetPassCmd = "/resetpassword";
                resetPassSyntax = "<player>";

                resetIpCmd = "/resetip";
                resetIpSyntax = "<player>";

                resetPinCmd = "/resetpin";
                resetPinSyntax = "<player>";

                resetAccountCmd = "/resetaccount";
                resetAccountSyntax = "<player>";

                moveCmd = "/moveaccount";
                moveSyntax = "<old> <new>";

                spawnCmd = "/loginspawn";
                spawnSyntax = "<setspawn/delspawn> <type>";

                ipManagerCmd = "/ipmanager";
                ipManagerSyntax = "<info/unban/bypass/unbypass> <player>";

                ipTrustCmd = "/iptrust";
                ipTrustSyntax = "<info/set/reset> <IP> <score>";

                reloadCmd = "/astralogin";
                reloadSyntax = "reload";

                updateCmd = "/astralogin";
                updateSyntax = "update";
                break;
        }

        sender.sendMessage(p + "§b§l=== " + helpTitle + " ===");
        sender.sendMessage(p + "§7" + versionLabel + " §f" + plugin.getDescription().getVersion() + " §7| " + authorLabel + " §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + playerCmds);
        sender.sendMessage(p + " §f" + regCmd + " " + regSyntax);
        sender.sendMessage(p + " §f" + loginCmd + " " + loginSyntax);
        sender.sendMessage(p + " §f" + logoutCmd);
        sender.sendMessage(p + " §f" + changePassCmd + " " + changePassSyntax);
        sender.sendMessage(p + " §f" + pinCmd + " " + pinSyntax);
        sender.sendMessage(p + " §f" + forgotCmd + " " + forgotSyntax);
        sender.sendMessage(p + " §f" + tfaCmd + " " + tfaSyntax);

        if (sender.hasPermission("astralogin.account") || sender.hasPermission("astralogin.reload") || sender.hasPermission("astralogin.update")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + adminCmds);
            sender.sendMessage(p + " §f" + accountCmd + " " + accountSyntax);
            sender.sendMessage(p + " §f" + listCmd);
            sender.sendMessage(p + " §f" + resetPassCmd + " " + resetPassSyntax);
            sender.sendMessage(p + " §f" + resetIpCmd + " " + resetIpSyntax);
            sender.sendMessage(p + " §f" + resetPinCmd + " " + resetPinSyntax);
            sender.sendMessage(p + " §f" + resetAccountCmd + " " + resetAccountSyntax);
            sender.sendMessage(p + " §f" + moveCmd + " " + moveSyntax);
            sender.sendMessage(p + " §f" + spawnCmd + " " + spawnSyntax);
            sender.sendMessage(p + " §f" + ipManagerCmd + " " + ipManagerSyntax);
            sender.sendMessage(p + " §f" + ipTrustCmd + " " + ipTrustSyntax);
            sender.sendMessage(p + " §f" + reloadCmd + " " + reloadSyntax);
            sender.sendMessage(p + " §f" + updateCmd + " " + updateSyntax);
        }

        sender.sendMessage(p + "§b§l=================================");
    }

    // --- METODY POWIADOMIEŃ PLIKÓW I KONFIGURACJI ---

    public void sendConfigUpdateNotice() {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aZaktualizowano plik konfiguracyjny";
            case "de" -> "§aAktualisierte Konfigurationsdatei";
            case "es" -> "§aArchivo de configuración actualizado";
            default -> "§aUpdated configuration file";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Błąd podczas zapisu pliku konfiguracyjnego. Błąd:";
            case "de" -> "Fehler beim Speichern der Konfigurationsdatei. Fehler:";
            case "es" -> "Error al guardar el archivo de configuración. Error:";
            default -> "Error while saving config. Error:";
        };
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aZaktualizowano plik językowy:";
            case "de" -> "§aAktualisierte Sprachdatei:";
            case "es" -> "§aArchivo de idioma actualizado:";
            default -> "§aUpdated language file:";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się zaktualizować pliku językowego (" + fileName + "). Błąd:";
            case "de" -> "Sprachdatei konnte nicht aktualisiert werden (" + fileName + "). Fehler:";
            case "es" -> "Error al actualizar el archivo de idioma (" + fileName + "). Error:";
            default -> "Failed to update language file (" + fileName + "). Error:";
        };
        plugin.getLogger().severe(msg + " " + error);
    }

    // --- METODY POWIADOMIEŃ BŁĘDÓW I SYSTEMOWYCH ---

    public void sendPlayerLocationReadError(String playerName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Błąd podczas odczytu pozycji dla " + playerName;
            case "de" -> "Fehler beim Lesen des Standorts für " + playerName;
            case "es" -> "Error al leer la ubicación de " + playerName;
            default -> "Error while reading location for " + playerName;
        };
        plugin.getLogger().severe(msg);
    }

    public void sendLoggerError(Exception e) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" ->
                    "KRYTYCZNY BŁĄD: Nie udało się aktywować filtra logów! Hasła mogą być widoczne w konsoli. Błąd: " + e.getMessage();
            case "de" ->
                    "KRITISCHER FEHLER: Log-Filter konnte nicht aktiviert werden! Passwörter könnten in der Konsole sichtbar sein. Fehler: " + e.getMessage();
            case "es" ->
                    "ERROR CRÍTICO: ¡No se pudo activar el filtro de registros! Las contraseñas pueden ser visibles en la consola. Error: " + e.getMessage();
            default ->
                    "CRITICAL ERROR: Failed to activate log filter! Passwords may be visible in console. Error: " + e.getMessage();
        };
        plugin.getLogger().severe(msg);
    }

    public void sendPremiumCheckError(String player, Exception e) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się zweryfikować gracza " + player + ". Błąd: " + e.getMessage();
            case "de" -> "Spieler " + player + " konnte nicht verifiziert werden. Fehler: " + e.getMessage();
            case "es" -> "No se pudo verificar al jugador " + player + ". Error: " + e.getMessage();
            default -> "Failed to verify player " + player + ". Error: " + e.getMessage();
        };
        plugin.getLogger().severe(msg);
    }

    public void sendLogSaveError(String fileName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się zapisać logu bezpieczeństwa do pliku: " + fileName;
            case "de" -> "Sicherheitslog konnte nicht in der Datei gespeichert werden: " + fileName;
            case "es" -> "No se pudo guardar el registro de seguridad en el archivo: " + fileName;
            default -> "Failed to save security log to file: " + fileName;
        };
        plugin.getLogger().severe(msg);
    }

    public void sendBackupSaveError(String error) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się utworzyć kopii zapasowej. Błąd:";
            case "de" -> "Sicherung konnte nicht erstellt werden. Fehler:";
            case "es" -> "Error al crear la copia de seguridad. Error:";
            default -> "Backup failed. Error:";
        };
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendBackupSave(String fileName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aPomyślnie utworzono kopię zapasową: §e";
            case "de" -> "§aBackup erfolgreich erstellt: §e";
            case "es" -> "§aCopia de seguridad creada con éxito: §e";
            default -> "§aBackup successfully created: §e";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    public void sendAutomaticBackupSave(String fileName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aPomyślnie utworzono automatyczną kopię zapasową: §e";
            case "de" -> "§aAutomatisches Backup erfolgreich erstellt: §e";
            case "es" -> "§aCopia de seguridad automática creada con éxito: §e";
            default -> "§aAutomatic backup successfully created: §e";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + fileName);
    }

    // --- MIGRACJA I SESJE ---

    public void sendMigrationNotice(String oldName, String newName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§6Migracja: §f" + oldName + " §7-> §f" + newName + "...";
            case "de" -> "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
            case "es" -> "§6Migración: §f" + oldName + " §7-> §f" + newName + "...";
            default -> "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSuccessMigrationNotice(String name) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aPlik/Sekcja §f" + name + " §azostała pomyślnie przeniesiona!";
            case "de" -> "§aDatei/Abschnitt §f" + name + " §awurde erfolgreich migriert!";
            case "es" -> "§a¡Archivo/Sección §f" + name + " §amigrado con éxito!";
            default -> "§aFile/Section §f" + name + " §ahas been successfully migrated!";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendErrorMigrationNotice(String action) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§cBłąd podczas migracji: §f" + action;
            case "de" -> "§cFehler bei der Migration: §f" + action;
            case "es" -> "§cError durante la migración: §f" + action;
            default -> "§cError during migration: §f" + action;
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSessionsLoaded(int count) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§eWczytano §6" + count + " §eaktywnych sesji z pliku";
            case "de" -> "§e" + count + " §eaktive Sitzungen aus Datei geladen";
            case "es" -> "§eSe cargaron §6" + count + " §ecesiones activas desde el archivo";
            default -> "§eLoaded §6" + count + " §eactive sessions from file";
        };
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
        String farewell = switch (lang) {
            case "pl" -> {
                status = "§cWyłączony";
                yield "§eDziękujemy, że z nas korzystasz! Do zobaczenia!";
            }
            case "de" -> {
                status = "§cDeaktiviert";
                yield "§eVielen Dank für die Nutzung! Bis zum nächsten Mal!";
            }
            case "es" -> {
                status = "§cDesactivado";
                yield "§e¡Gracias por elegirnos! ¡Hasta la próxima!";
            }
            default -> {
                status = "§cDisabled";
                yield "§eThanks for choosing us! See you next time!";
            }
        };

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // --- METODY POWIADOMIEŃ WERSJI I AKTUALIZACJI ---

    public void sendLatestVersionNotice(CommandSender target) {
        String prefix = getPrefix(target);
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aAstraLogin jest w najnowszej wersji §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            case "de" ->
                    "§aAstraLogin ist in der neuesten Version enthalten §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            case "es" -> "§aAstraLogin está en la última versión §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            default -> "§aAstraLogin is in the latest version §f(§ev" + plugin.getDescription().getVersion() + "§f)";
        };

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

    public void sendVersionDevNotice(
            CommandSender target,
            String latestPublicVersion
    ) {
        String lang = getLang().toLowerCase();
        String devTitle;
        String publicInfo;
        String download;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz nowszej wersji: §fv";
                publicInfo = "§eNajnowsza publiczna wersja AstraLogin to: §fv";
                download = "§aPobierz: ";
                break;

            case "de":
                devTitle = "§bSie verwenden eine neuere Version: §fv";
                publicInfo = "§eDie neueste öffentliche Version von AstraLogin ist: §fv";
                download = "§aHerunterladen: ";
                break;

            case "es":
                devTitle = "§bEstás utilizando una versión más reciente: §fv";
                publicInfo = "§eLa última versión pública de AstraLogin es: §fv";
                download = "§aDescargar: ";
                break;

            default:
                devTitle = "§bYou are using a newer version: §fv";
                publicInfo = "§eThe latest public version of AstraLogin is: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(
                devTitle + plugin.getDescription().getVersion()
        );
        target.sendMessage(publicInfo + latestPublicVersion);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + latestPublicVersion);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendStableReleaseNotice(
            CommandSender target,
            String version
    ) {
        String lang = getLang().toLowerCase();
        String title;
        String info;
        String download;

        switch (lang) {
            case "pl":
                title = "§aDostępna jest stabilna wersja AstraLogin: §f";
                info = "§7Zalecana aktualizacja z wersji testowej.";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§aEine stabile AstraLogin-Version ist verfügbar: §f";
                info = "§7Ein Update von der Testversion wird empfohlen.";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§aYa está disponible una versión estable de AstraLogin: §f";
                info = "§7Se recomienda actualizar desde la versión de prueba.";
                download = "§aDescargar: ";
                break;
            default:
                title = "§aA stable AstraLogin release is available: §f";
                info = "§7Updating from the test version is recommended.";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(info);
        target.sendMessage(download
                + "§f§nhttps://modrinth.com/plugin/astralogin/version/"
                + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download = switch (lang) {
            case "pl" -> {
                title = "§cDostępna jest WIELKA aktualizacja AstraLogin: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§cEin GROSSES AstraLogin-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§cUna actualización MAYOR de AstraLogin está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§cA MAJOR AstraLogin update is available: §fv";
                yield "§aDownload: ";
            }
        };

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
        String download = switch (lang) {
            case "pl" -> {
                title = "§eDostępna jest nowa aktualizacja AstraLogin: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§eEin neues AstraLogin-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§eUna nueva actualización de AstraLogin está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§eA new AstraLogin update is available: §fv";
                yield "§aDownload: ";
            }
        };

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
        String download = switch (lang) {
            case "pl" -> {
                title = "§bDostępna jest mała aktualizacja AstraLogin: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§bEin kleineres AstraLogin-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§bUna pequeña actualización de AstraLogin está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§bA minor AstraLogin update is available: §fv";
                yield "§aDownload: ";
            }
        };

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się sprawdzić aktualizacji";
            case "de" -> "Fehler beim Überprüfen auf Updates";
            case "es" -> "Error al buscar actualizaciones";
            default -> "Failed to check for updates";
        };
        plugin.getLogger().warning(msg);
    }

    // --- EVENTY SEZONOWE ---

    public void sendSeasonalEventActivated(String eventName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "[SeasonalEvents] Aktywowano wydarzenie sezonowe: " + eventName;
            case "de" -> "[SeasonalEvents] Saisonaler Event aktiviert: " + eventName;
            case "es" -> "[SeasonalEvents] Evento estacional activado: " + eventName;
            default -> "[SeasonalEvents] Activated seasonal event: " + eventName;
        };
        plugin.getLogger().info(msg);
    }
}