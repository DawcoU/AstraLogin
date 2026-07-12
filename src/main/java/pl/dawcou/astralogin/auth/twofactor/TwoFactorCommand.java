package pl.dawcou.astralogin.auth.twofactor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TwoFactorCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final TwoFactorManager twoFactorManager;
    private final LoginSystem loginSystem;

    public TwoFactorCommand(AstraLogin plugin, TwoFactorManager twoFactorManager, LoginSystem loginSystem) {
        this.plugin = plugin;
        this.twoFactorManager = twoFactorManager;
        this.loginSystem = loginSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // ==========================================
        // KOMENDA: /zresetuj2fa lub /reset2fa
        // ==========================================
        if (command.getName().equalsIgnoreCase("zresetuj2fa") || command.getName().equalsIgnoreCase("reset2fa")) {
            if (!sender.hasPermission("astralogin.reset2fa")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("usage-reset-2fa"));
                return true;
            }

            String targetName = args[0];

            OfflinePlayer offlineP = Bukkit.getOfflinePlayer(targetName);
            UUID targetUUID = offlineP.getUniqueId();

            FileConfiguration accountsConfig = plugin.getAccountDataManager().getConfig();

            if (twoFactorManager.isSetupActive(targetUUID)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-player-is-setting-up")
                        .replace("%target%", targetName));
                return true;
            }

            if (!accountsConfig.contains("accounts." + targetUUID + ".2fa-enabled")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-not-found").replace("%target%", targetName));
                return true;
            }

            if (loginSystem.isWaitingFor2FA(targetUUID)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-player-is-waiting").replace("%target%", targetName));
                return true;
            }

            twoFactorManager.invalidateSetup(targetUUID);
            twoFactorManager.delete2FA(targetUUID);

            Player targetP = offlineP.getPlayer();
            if (targetP != null && targetP.isOnline()) {
                String MessageReset2FA = plugin.getLanguageManager().getMessage("player-reset-2fa");
                targetP.sendMessage(Component.text(MessageReset2FA));
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("admin-reset-2fa-success").replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " Removed two-step verification for the player " + targetName);

            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("only-players"));
            return true;
        }

        UUID uuid = p.getUniqueId();

        if (args.length == 0) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-usage"));
            return true;
        }

        // SETUP
        if (args[0].equalsIgnoreCase("setup")) {
            if (plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuid + ".2fa-enabled", false)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-already-enabled"));
                return true;
            }

            if (twoFactorManager.isSetupActive(uuid)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-setup-already-active"));
                return true;
            }

            plugin.getLogManager().log("Player " + p.getName() + " started configuring 2FA");

            String secret = twoFactorManager.startSetup(uuid);

            // Wysyłamy instrukcję z kluczem tajnym
            p.sendMessage(plugin.getLanguageManager().getMessage("2fa-setup-instructions")
                    .replace("%secret%", secret));

            // 2. Pobieramy wiadomość kodów zapasowych jako jeden czysty String
            String backupMessage = plugin.getLanguageManager().getMessage("2fa-setup-backups-codes");
            List<String> backupCodes = twoFactorManager.getPendingBackupCodes(uuid);

            if (backupMessage != null && !backupMessage.isEmpty() && backupCodes != null) {
                StringBuilder codesBuilder = new StringBuilder();
                for (int i = 0; i < backupCodes.size(); i++) {
                    codesBuilder.append(backupCodes.get(i));
                    if (i < backupCodes.size() - 1) {
                        codesBuilder.append("\n");
                    }
                }

                String finalMessage = backupMessage.replace("%codes%", codesBuilder.toString());

                p.sendMessage(Component.text(finalMessage));
            }

            // Optymalizacja: Pobieramy formaty wiadomości RAZ przed uruchomieniem schedulera
            String timerFormat = plugin.getLanguageManager().getMessage("2fa-timer");
            String expiredMessage = plugin.getLanguageManager().getWithPrefix("2fa-expired");

            plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, (task) -> {
                // Jeśli gracz wyjdzie, po prostu anulujemy bez wysyłania wiadomości
                if (!p.isOnline()) {
                    task.cancel();
                    return;
                }

                // Sprawdzenie czy setup jest nadal aktywny w managerze
                if (!twoFactorManager.isSetupActive(uuid)) {
                    p.sendMessage(expiredMessage);
                    plugin.getLogManager().log("Player " + p.getName() + " didn't have time to configure 2FA");
                    task.cancel();
                    return;
                }

                String secondsLeft = twoFactorManager.getRemainingTime(uuid);

                p.sendActionBar(Component.text(
                        timerFormat.replace("%seconds%", secondsLeft)));
            }, 0, 1, TimeUnit.SECONDS);
            return true;
        }

        // UNSETUP
        if (args[0].equalsIgnoreCase("unsetup")) {
            if (!plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuid + ".2fa-enabled", false)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-not-enabled"));
                return true;
            }

            if (args.length != 2) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-unsetup-instructions"));
                return true;
            }

            String inputCode = args[1];

            // NOWOŚĆ: Logowanie unsetupu za pomocą kodu zapasowego
            if (twoFactorManager.useBackupCode(uuid, inputCode)) {
                loginSystem.removeWaitingFor2FA(uuid);

                if (!loginSystem.getLoggedIn().contains(uuid)) {
                    loginSystem.finishLogin(p);
                }

                twoFactorManager.delete2FA(uuid);
                // TUTAJ: Log z info, że użyto kodu zapasowego!
                plugin.getLogManager().log("Player " + p.getName() + " removed 2FA protection using a backup code");
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-removed-success"));
                return true;
            }

            // Standardowa ścieżka z kodem Google Authenticator
            String secret = twoFactorManager.getSavedSecret(uuid);
            if (secret == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-not-enabled"));
                return true;
            }

            try {
                int code = Integer.parseInt(inputCode);
                if (twoFactorManager.verifyCode(secret, code)) {
                    loginSystem.removeWaitingFor2FA(uuid);

                    if (!loginSystem.getLoggedIn().contains(uuid)) {
                        loginSystem.finishLogin(p);
                    }

                    twoFactorManager.delete2FA(uuid);
                    plugin.getLogManager().log("Player " + p.getName() + " removed 2FA protection from his account");
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-removed-success"));
                } else {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-wrong-code"));
                }
            } catch (NumberFormatException e) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-wrong-code"));
            }
            return true;
        }

        String rawInput = String.join("", args).trim();
        String ip = p.getAddress().getAddress().getHostAddress();

        // SCENARIUSZ Z KODEM ZAPASOWYM PODCZAS LOGOWANIA
        if (loginSystem.isWaitingFor2FA(uuid) && rawInput.contains("-")) {
            if (twoFactorManager.useBackupCode(uuid, rawInput)) {
                loginSystem.removeWaitingFor2FA(uuid);
                loginSystem.finishLogin(p);
                if (plugin.getConfig().getBoolean("features.2fa.session.enabled")) {
                    plugin.getSessionManager().saveSession2FA(uuid, ip);
                }
                plugin.getLogManager().log("Player " + p.getName() + " entered a valid backup code and was logged in");
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-login"));
                p.sendTitle(
                        plugin.getLanguageManager().getMessage("title-login"),
                        plugin.getLanguageManager().getMessage("subtitle-login"),
                        10, 40, 10
                );
            } else {
                if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                    plugin.getAttemptManager().dodajProbe(p, "2FA");
                }
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-wrong-code"));
            }
            return true;
        }

        if (rawInput.contains("-") && !loginSystem.isWaitingFor2FA(uuid)) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-not-needed"));
            return true;
        }

        // Dopiero tutaj parsujemy czyste cyfry (np. usuwając spakowane spacje dla wygody!)
        int code;
        try {
            code = Integer.parseInt(rawInput.replace(" ", ""));
        } catch (NumberFormatException e) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-usage"));
            return true;
        }

        // SCENARIUSZ 1: Gracz jest w trakcie konfiguracji (aktywacja)
        if (twoFactorManager.isSetupActive(uuid)) {
            String secret = twoFactorManager.getPendingSecret(uuid);

            if (twoFactorManager.verifyCode(secret, code)) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, saveTask -> {
                    twoFactorManager.save2FA(uuid, secret);
                    plugin.getLogManager().log("Player " + p.getName() + " successfully completed 2FA setup");
                    p.getScheduler().run(plugin, syncTask -> {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-success"));
                        p.sendTitle("", "", 0, 10, 0);
                    }, null);
                });
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-wrong-code"));
            }
            return true;
        }

        // SCENARIUSZ 2: Gracz jest w trakcie logowania (poczekalnia 2FA)
        if (loginSystem.isWaitingFor2FA(uuid)) {
            String secret = twoFactorManager.getSavedSecret(uuid);

            if (secret != null && twoFactorManager.verifyCode(secret, code)) {
                loginSystem.removeWaitingFor2FA(uuid);
                loginSystem.finishLogin(p);
                if (plugin.getConfig().getBoolean("features.2fa.session.enabled")) {
                    plugin.getSessionManager().saveSession2FA(uuid, ip);
                }
                plugin.getLogManager().log("Player " + p.getName() + " entered the correct 2FA code and was logged in");
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("success-login"));
                p.sendTitle(
                        plugin.getLanguageManager().getMessage("title-login"),
                        plugin.getLanguageManager().getMessage("subtitle-login"),
                        10, 40, 10
                );
            } else {
                if (plugin.getConfig().getInt("features.attempts.max", 3) > 0) {
                    plugin.getAttemptManager().dodajProbe(p, "2FA");
                }
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-wrong-code"));
            }
            return true;
        }
        p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-not-needed"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("2fa")) return null;

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> hints = new java.util.ArrayList<>();

            if (input.matches("\\d+")) {
                return java.util.Collections.emptyList();
            }

            hints.add("setup");
            hints.add("unsetup");
            hints.add("<kod>");

            return hints.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Podpowiedź kodu po unsetup (np. /2fa unsetup <kod>) - zwracamy pusto
        return java.util.Collections.emptyList();
    }
}