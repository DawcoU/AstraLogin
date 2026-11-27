package pl.dawcou.astralogin.commands.twofactor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.EmailSender;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorMethod;
import pl.dawcou.astralogin.auth.security.twofactor.method.EmailTwoFactorManager;
import pl.dawcou.astralogin.auth.security.twofactor.method.TotpManager;
import pl.dawcou.astralogin.system.SoundManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TwoFactorCommands implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final TwoFactorManager twoFactorManager;
    private final LoginSystem loginSystem;

    // Sets distinguish the purpose of an email verification code because EmailTwoFactorManager only tracks the code and expiration.
    private final Set<UUID> emailSetupRequests = ConcurrentHashMap.newKeySet();
    private final Set<UUID> completingEmailSetups = ConcurrentHashMap.newKeySet();
    private final Set<UUID> emailUnsetupRequests = ConcurrentHashMap.newKeySet();

    public TwoFactorCommands(AstraLogin plugin, TwoFactorManager twoFactorManager, LoginSystem loginSystem) {
        this.plugin = plugin;
        this.twoFactorManager = twoFactorManager;
        this.loginSystem = loginSystem;
    }

    // --------------------------------------------------
    // Command routing
    // --------------------------------------------------

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        Player player = sender instanceof Player p ? p : null;

        if (isResetCommand(command)) {
            return handleAdministrativeReset(sender, player, args);
        }

        if (player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("setup")) {
            if (args.length != 2) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.setup-method-usage"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }
            return handleSetup(player, uuid, args[1]);
        }

        if (args[0].equalsIgnoreCase("unsetup")) {
            if (args.length == 1) {
                return handleUnsetup(player, uuid, null);
            }
            if (args.length == 2) {
                return handleUnsetup(player, uuid, args[1]);
            }
            sendUsage(player);
            return true;
        }

        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        handleCode(player, uuid, args[0]);
        return true;
    }

    // --------------------------------------------------
    // Setup flow and timer
    // --------------------------------------------------

    private boolean handleSetup(Player player, UUID uuid, String methodInput) {
        if (twoFactorManager.has2FA(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.already-enabled"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (isPlayerSettingUp(uuid) || twoFactorManager.getEmailManager().hasPendingCode(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.setup-already-active"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        TwoFactorMethod method = switch (methodInput.toLowerCase()) {
            case "totp" -> TwoFactorMethod.TOTP;
            case "mail", "email" -> TwoFactorMethod.MAIL;
            default -> null;
        };

        if (method == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.setup-method-usage"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (method == TwoFactorMethod.TOTP) {
            startTotpSetup(player, uuid);
        } else {
            startEmailSetup(player, uuid);
        }
        return true;
    }

    private void startTotpSetup(Player player, UUID uuid) {
        TotpManager totpManager = twoFactorManager.getTotpManager();
        String secret = totpManager.startSetup(uuid);
        plugin.getLogManager().log("Player " + player.getName() + " started configuring TOTP 2FA");

        for (String line : plugin.getLanguageManager().getMessageList("twofactor.setup.instructions")) {
            player.sendMessage(line.replace("%secret%", secret));
        }

        sendBackupCodes(player, totpManager.getPendingBackupCodes(uuid));
        startSetupTimer(player, uuid, TwoFactorMethod.TOTP);
    }

    private void startEmailSetup(Player player, UUID uuid) {
        EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
        if (!emailManager.isConfigured()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.not-configured"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return;
        }

        String email = emailManager.getEmailAddress(uuid);
        if (email == null || email.isBlank()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.not-set"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return;
        }

        // Generate backup codes before sending the email; save them only after verification.
        List<String> backupCodes = twoFactorManager.getTotpManager().startBackupCodeSetup(uuid);
        if (backupCodes == null || backupCodes.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.save-failed"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            twoFactorManager.invalidateSetup(uuid);
            return;
        }

        emailSetupRequests.add(uuid);
        plugin.getLogManager().log("Player " + player.getName() + " started configuring MAIL 2FA");

        emailManager.sendVerificationCode(uuid, email, success -> {
            if (!player.isOnline()) {
                clearEmailSetupState(uuid);
                twoFactorManager.invalidateSetup(uuid);
                return;
            }

            if (!success) {
                clearEmailSetupState(uuid);
                twoFactorManager.invalidateSetup(uuid);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.send-failed"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return;
            }

            for (String line : plugin.getLanguageManager().getMessageList("twofactor.mail.setup-instructions")) {
                player.sendMessage(line.replace("%email%", email));
            }

            sendBackupCodes(player, backupCodes);
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.setup-code-sent").replace("%email%", email));
            startSetupTimer(player, uuid, TwoFactorMethod.MAIL);
        });
    }

    private void sendBackupCodes(Player player, List<String> backupCodes) {
        if (backupCodes == null || backupCodes.isEmpty()) {
            return;
        }

        List<String> template = plugin.getLanguageManager().getMessageList("twofactor.setup.backup-codes");
        if (template == null) {
            return;
        }

        for (String line : template) {
            if (line.contains("%codes%")) {
                for (String code : backupCodes) {
                    player.sendMessage(line.replace("%codes%", code));
                }
            } else {
                player.sendMessage(line);
            }
        }
    }

    private void startSetupTimer(Player player, UUID uuid, TwoFactorMethod method) {
        String timerFormat = plugin.getLanguageManager().getMessage("twofactor.setup-timer");
        String expiredMessage = plugin.getLanguageManager().getWithPrefix("twofactor.expired");

        plugin.getSchedulerManager().runAsyncRepeating(task -> {
            if (!player.isOnline()) {
                task.cancel();
                if (method == TwoFactorMethod.MAIL) {
                    clearEmailSetupState(uuid);
                }
                twoFactorManager.invalidateSetup(uuid);
                return;
            }

            if (twoFactorManager.has2FA(uuid)) {
                task.cancel();
                if (method == TwoFactorMethod.MAIL) {
                    clearEmailSetupState(uuid);
                }
                return;
            }

            if (method == TwoFactorMethod.MAIL && completingEmailSetups.contains(uuid)) {
                task.cancel();
                return;
            }

            String remainingTime;
            if (method == TwoFactorMethod.TOTP) {
                TotpManager totpManager = twoFactorManager.getTotpManager();
                if (!totpManager.isSetupActive(uuid)) {
                    task.cancel();
                    player.sendMessage(expiredMessage);
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    plugin.getLogManager().log("Player " + player.getName() + " did not complete TOTP 2FA setup in time");
                    return;
                }
                remainingTime = totpManager.getRemainingTime(uuid);
            } else {
                if (!emailSetupRequests.contains(uuid)) {
                    task.cancel();
                    return;
                }

                EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
                if (!emailManager.hasPendingCode(uuid)) {
                    task.cancel();
                    clearEmailSetupState(uuid);
                    twoFactorManager.invalidateSetup(uuid);
                    player.sendMessage(expiredMessage);
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    plugin.getLogManager().log("Player " + player.getName() + " did not complete MAIL 2FA setup in time");
                    return;
                }

                remainingTime = TimeUtils.formatTime(emailManager.getRemainingTime(uuid));
            }

            player.sendActionBar(timerFormat.replace("%seconds%", remainingTime));
        }, 0, 1, TimeUnit.SECONDS);
    }

    // --------------------------------------------------
    // Code verification
    // --------------------------------------------------

    private void handleCode(Player player, UUID uuid, String inputCode) {
        if (inputCode == null || inputCode.isBlank()) {
            sendUsage(player);
            return;
        }

        String code = inputCode.trim();
        String ip = getPlayerIp(player);

        if (emailSetupRequests.contains(uuid)) {
            handleEmailSetupCode(player, uuid, code);
            return;
        }
        if (twoFactorManager.getTotpManager().isSetupActive(uuid)) {
            handleTotpSetupCode(player, uuid, code);
            return;
        }
        if (emailUnsetupRequests.contains(uuid)) {
            handleEmailUnsetupCode(player, uuid, code);
            return;
        }
        if (loginSystem.getTwoFactorSystem().isWaitingFor2FA(uuid)) {
            handleLoginCode(player, uuid, code, ip);
            return;
        }

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.not-needed"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
    }

    private void handleTotpSetupCode(Player player, UUID uuid, String inputCode) {
        if (!inputCode.matches("\\d{6}")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        TotpManager totpManager = twoFactorManager.getTotpManager();
        String secret = totpManager.getPendingSecret(uuid);
        if (!totpManager.verifyCode(secret, Integer.parseInt(inputCode))) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.WRONG_PASSWORD);
            return;
        }

        totpManager.save2FA(uuid, secret, success -> {
            if (!player.isOnline()) {
                return;
            }
            if (!success) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.save-failed"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                twoFactorManager.invalidateSetup(uuid);
                return;
            }

            plugin.getLogManager().log("Player " + player.getName() + " successfully completed TOTP 2FA setup");
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.success"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
        });
    }

    private void handleEmailSetupCode(Player player, UUID uuid, String inputCode) {
        if (!inputCode.matches("\\d{6}")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
        if (!emailManager.verifyCode(uuid, inputCode)) {
            if (!emailManager.hasPendingCode(uuid)) {
                clearEmailSetupState(uuid);
                twoFactorManager.invalidateSetup(uuid);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.code-expired"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            } else {
                loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, getPlayerIp(player));
            }
            return;
        }

        String email = emailManager.getEmailAddress(uuid);
        if (!EmailSender.isValidEmail(email)) {
            clearEmailSetupState(uuid);
            twoFactorManager.invalidateSetup(uuid);
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.save-failed"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return;
        }

        // Mark completion immediately so the setup timer does not treat the consumed code as expiration.
        emailSetupRequests.remove(uuid);
        completingEmailSetups.add(uuid);

        twoFactorManager.getTotpManager().savePendingBackupCodes(uuid, success -> {
            completingEmailSetups.remove(uuid);

            if (success) {
                plugin.getPlayerDataManager().set(uuid, "security.two-factor.enabled", true);
                plugin.getPlayerDataManager().set(uuid, "security.two-factor.method", TwoFactorMethod.MAIL.getConfigValue());
                plugin.getPlayerDataManager().remove(uuid, "security.two-factor.secret");
                emailManager.invalidateCode(uuid);
            }

            if (!player.isOnline()) {
                if (!success) {
                    twoFactorManager.invalidateSetup(uuid);
                }
                return;
            }

            if (!success) {
                twoFactorManager.invalidateSetup(uuid);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.save-failed"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return;
            }

            plugin.getLogManager().log("Player " + player.getName() + " successfully completed MAIL 2FA setup");
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.success"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
        });
    }

    // --------------------------------------------------
    // Login verification
    // --------------------------------------------------

    private void handleLoginCode(Player player, UUID uuid, String inputCode, String ip) {
        if (inputCode.contains("-")) {
            verifyBackupCode(player, uuid, inputCode, ip, false);
            return;
        }

        TwoFactorMethod method = twoFactorManager.getMethod(uuid);
        if (method == TwoFactorMethod.MAIL) {
            if (!inputCode.matches("\\d{6}")) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return;
            }

            EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
            if (!emailManager.verifyCode(uuid, inputCode)) {
                if (!emailManager.hasPendingCode(uuid)) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.login.code-expired"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                } else {
                    loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, ip);
                }
                return;
            }

            loginSystem.getTwoFactorSystem().completeTwoFactorLogin(player, ip, false);
            return;
        }

        if (method == TwoFactorMethod.TOTP) {
            if (!inputCode.matches("\\d{6}")) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return;
            }

            TotpManager totpManager = twoFactorManager.getTotpManager();
            String savedSecret = totpManager.getSavedSecret(uuid);
            if (totpManager.verifyCode(savedSecret, Integer.parseInt(inputCode))) {
                loginSystem.getTwoFactorSystem().completeTwoFactorLogin(player, ip, false);
            } else {
                loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, ip);
            }
            return;
        }

        sendVerificationError(player);
    }

    // --------------------------------------------------
    // Unsetup and removal
    // --------------------------------------------------

    private boolean handleUnsetup(Player player, UUID uuid, String inputCode) {
        if (!twoFactorManager.has2FA(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.not-enabled"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        TwoFactorMethod method = twoFactorManager.getMethod(uuid);
        if (inputCode == null) {
            if (method == TwoFactorMethod.MAIL) {
                requestMailUnsetupCode(player, uuid);
            } else {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.unsetup.instructions"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String code = inputCode.trim();
        if (code.contains("-")) {
            verifyBackupCode(player, uuid, code, getPlayerIp(player), true);
            return true;
        }

        if (method == TwoFactorMethod.MAIL) {
            if (!emailUnsetupRequests.contains(uuid)) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.unsetup.mail-required"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }
            handleEmailUnsetupCode(player, uuid, code);
            return true;
        }

        if (method == TwoFactorMethod.TOTP) {
            if (!code.matches("\\d{6}")) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            TotpManager totpManager = twoFactorManager.getTotpManager();
            String savedSecret = totpManager.getSavedSecret(uuid);
            if (totpManager.verifyCode(savedSecret, Integer.parseInt(code))) {
                completeTwoFactorRemoval(player, uuid, false);
            } else {
                loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, getPlayerIp(player));
            }
            return true;
        }

        sendVerificationError(player);
        return true;
    }

    private void requestMailUnsetupCode(Player player, UUID uuid) {
        EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
        if (!emailManager.isConfigured()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.not-configured"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return;
        }

        if (emailUnsetupRequests.contains(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.unsetup.code-pending"));
            return;
        }

        String email = emailManager.getEmailAddress(uuid);
        if (email == null || email.isBlank()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.not-set"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return;
        }

        if (emailManager.hasPendingCode(uuid)) {
            emailManager.invalidateCode(uuid);
        }

        emailUnsetupRequests.add(uuid);

        emailManager.sendVerificationCode(uuid, email, success -> {
            if (!player.isOnline()) {
                emailUnsetupRequests.remove(uuid);
                emailManager.invalidateCode(uuid);
                return;
            }

            if (!success) {
                emailUnsetupRequests.remove(uuid);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.send-failed"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return;
            }

            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.unsetup.code-sent").replace("%email%", email));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
        });
    }

    private void handleEmailUnsetupCode(Player player, UUID uuid, String inputCode) {
        if (!inputCode.matches("\\d{6}")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.wrong-code"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return;
        }

        EmailTwoFactorManager emailManager = twoFactorManager.getEmailManager();
        if (!emailManager.verifyCode(uuid, inputCode)) {
            if (!emailManager.hasPendingCode(uuid)) {
                emailUnsetupRequests.remove(uuid);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.mail.code-expired"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            } else {
                loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, getPlayerIp(player));
            }
            return;
        }

        emailUnsetupRequests.remove(uuid);
        completeTwoFactorRemoval(player, uuid, false);
    }

    private void verifyBackupCode(Player player, UUID uuid, String inputCode, String ip, boolean removing) {
        twoFactorManager.getTotpManager().useBackupCode(uuid, inputCode, result -> {
            if (!player.isOnline()) {
                return;
            }

            if (result == null) {
                sendVerificationError(player);
                return;
            }

            switch (result.status()) {
                case SUCCESS -> {
                    if (removing) {
                        completeTwoFactorRemoval(player, uuid, true);
                    } else {
                        loginSystem.getTwoFactorSystem().completeTwoFactorLogin(player, ip, true);
                    }
                }
                case INVALID_PASSWORD -> loginSystem.getTwoFactorSystem().handleInvalidTwoFactorCode(player, ip);
                case RATE_LIMITED_SERVER -> sendServerBusy(player);
                case RATE_LIMITED_PLAYER -> sendRateLimit(player, result.remainingSeconds());
                default -> sendVerificationError(player);
            }
        });
    }

    private void completeTwoFactorRemoval(Player player, UUID uuid, boolean backupCode) {
        loginSystem.getTwoFactorSystem().removeWaitingFor2FA(uuid);
        if (!loginSystem.isLoggedIn(uuid)) {
            loginSystem.finishLogin(player);
        }

        clearEmailSetupState(uuid);
        emailUnsetupRequests.remove(uuid);
        completingEmailSetups.remove(uuid);
        twoFactorManager.delete2FA(uuid);

        plugin.getLogManager().log(
                backupCode
                        ? "Player " + player.getName() + " removed 2FA protection using a backup code"
                        : "Player " + player.getName() + " removed 2FA protection"
        );

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.removed-success"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
    }

    // --------------------------------------------------
    // Administrative reset
    // --------------------------------------------------

    private boolean handleAdministrativeReset(CommandSender sender, Player admin, String[] args) {
        if (!sender.hasPermission("astralogin.reset2fa")) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-twofactor.usage"));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String inputName = args[0];
        UUID targetUUID = plugin.getAccountManager().getUuidByUsername(inputName);
        if (targetUUID == null) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.player-not-found").replace("%target%", inputName));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        String targetName = plugin.getAccountManager().getRegisteredName(inputName);
        if (targetName == null) {
            targetName = inputName;
        }

        if (isPlayerSettingUp(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-twofactor.target.setting-up").replace("%target%", targetName));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        if (!twoFactorManager.has2FA(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-twofactor.target.not-enabled").replace("%target%", targetName));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        if (loginSystem.getTwoFactorSystem().isWaitingFor2FA(targetUUID)) {
            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-twofactor.target.waiting").replace("%target%", targetName));
            if (admin != null) {
                plugin.getSoundManager().playSound(admin, SoundManager.SoundType.FAIL);
            }
            return true;
        }

        clearEmailSetupState(targetUUID);
        emailUnsetupRequests.remove(targetUUID);
        completingEmailSetups.remove(targetUUID);
        plugin.getSessionManager().getTwoFactorSessionManager().deleteSession2FA(targetUUID);
        twoFactorManager.delete2FA(targetUUID);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.kickPlayer(plugin.getLanguageManager().getMessage("reset-twofactor.player.kick"));
        }

        sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-twofactor.admin.success").replace("%player%", targetName));
        if (admin != null) {
            plugin.getSoundManager().playSound(admin, SoundManager.SoundType.SUCCESS);
        }

        plugin.getLogManager().log("Admin " + sender.getName() + " removed two-step verification for player " + targetName);
        return true;
    }

    // --------------------------------------------------
    // Security, state and helpers
    // --------------------------------------------------

    private boolean isResetCommand(Command command) {
        String name = command.getName();
        return name.equalsIgnoreCase("zresetuj2fa")
                || name.equalsIgnoreCase("reset2fa")
                || name.equalsIgnoreCase("del2fa");
    }

    private boolean isPlayerSettingUp(UUID uuid) {
        return twoFactorManager.getTotpManager().isSetupActive(uuid)
                || emailSetupRequests.contains(uuid)
                || completingEmailSetups.contains(uuid);
    }

    private void clearEmailSetupState(UUID uuid) {
        emailSetupRequests.remove(uuid);
        completingEmailSetups.remove(uuid);
    }

    private void sendServerBusy(Player player) {
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.RATE_LIMITED);
    }

    private void sendRateLimit(Player player, long remainingSeconds) {
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit").replace("%time%", TimeUtils.formatTime(remainingSeconds)));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.RATE_LIMITED);
    }

    private void sendVerificationError(Player player) {
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
    }

    private void sendUsage(Player player) {
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.usage"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
    }

    private String getPlayerIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return address.getAddress().getHostAddress();
    }

    // --------------------------------------------------
    // Tab completion
    // --------------------------------------------------

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("2fa")) {
            return null;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if (input.matches("\\d+")) {
                return Collections.emptyList();
            }

            return Stream.of("setup", "unsetup")
                    .filter(value -> value.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            String input = args[1].toLowerCase();
            return Stream.of("totp", "mail")
                    .filter(value -> value.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}