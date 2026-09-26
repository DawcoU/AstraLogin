package pl.dawcou.astralogin.commands.player;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.passwords.PasswordHasher;
import pl.dawcou.astralogin.auth.passwords.PasswordValidator;
import pl.dawcou.astralogin.system.utils.SoundManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PasswordsCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public PasswordsCommand(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("zresetujhaslo") || command.getName().equalsIgnoreCase("resetpassword")) {
            if (!sender.hasPermission("astralogin.resetpassword")) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.usage"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            String targetInput = args[0];
            UUID targetUUID = plugin.getAccountManager().getUuidByUsername(targetInput);

            if (targetUUID == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.no-account"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            String targetName = plugin.getAccountManager().getRegisteredNameIgnoreCase(targetInput);
            if (targetName == null) {
                targetName = targetInput;
            }

            if (!plugin.getPasswordManager().isRegistered(targetUUID)) {
                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.no-account"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            plugin.getPasswordManager().deletePassword(targetUUID);

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("reset-password.admin-success")
                    .replace("%player%", targetName));

            String adminName = sender.getName();
            plugin.getLogManager().log("Admin " + adminName + " reset password for player " + targetName);
            if (p != null) {
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
            }

            Player targetP = Bukkit.getPlayer(targetUUID);

            if (targetP != null && targetP.isOnline()) {
                String playerIP = targetP.getAddress().getAddress().getHostAddress();
                plugin.getIPManager().resetIPAttempts(playerIP);
                targetP.kickPlayer(plugin.getLanguageManager().getMessage("reset-password.player-kick"));
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("niepamietamhasla") || command.getName().equalsIgnoreCase("forgotpassword") || command.getName().equalsIgnoreCase("forgotpass")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length != 1) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.usage"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                return true;
            }

            String ip = p.getAddress().getAddress().getHostAddress();
            String PIN = args[0];
            UUID playerUUID = p.getUniqueId();

            String hashedPIN = plugin.getPinManager().getPIN(playerUUID);

            if (hashedPIN == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.no-has-pin"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                return true;
            }

            plugin.getSchedulerManager().runAsync(() -> {
                PasswordHasher.VerificationResult result = plugin.getPasswordManager().getPasswordHasher().verifyPassword(playerUUID, PIN, hashedPIN);

                switch (result.status()) {
                    case SUCCESS:
                        if (result.rehashNeeded() && result.newHash() != null) {
                            plugin.getPasswordManager().savePassword(playerUUID, result.newHash());
                        }

                        plugin.getSchedulerManager().runSync(() -> {
                            p.kickPlayer(plugin.getLanguageManager().getMessage("forgot-password.player-kick"));
                        });

                        plugin.getPasswordManager().deletePassword(playerUUID);

                        plugin.getLogManager().log("Player " + p.getName() + " reset his password using PIN");
                        break;

                    case INVALID_PASSWORD:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.wrong-pin"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.WRONG_PASSWORD);

                            plugin.getIpTrustManager().addTrustScore(ip, plugin.getIpTrustManager().getFailedPasswordPoints());
                            plugin.getAttemptManager().checkCrime(p, "PIN");
                        });
                        break;

                    case RATE_LIMITED_SERVER:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("auth.login.server-busy"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;

                    case RATE_LIMITED_PLAYER:
                        long seconds = result.remainingSeconds();
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                                    .replace("%time%", TimeUtils.formatTime(seconds)));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;

                    case ERROR:
                    default:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("auth.login.error"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;
                }
            });

            return true;
        }

        if (command.getName().equalsIgnoreCase("zmienhaslo") || command.getName().equalsIgnoreCase("changepassword")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length != 3) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.change-usage"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                return true;
            }

            String ip = p.getAddress().getAddress().getHostAddress();
            String oldPassword = args[0];
            String newPassword = args[1];
            String newPasswordConfirm = args[2];

            if (oldPassword.equals(newPassword)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.identical"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            UUID playerUUID = p.getUniqueId();

            String currentPassword = plugin.getPasswordManager().getPassword(playerUUID);
            if (currentPassword == null) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            if (!newPassword.equals(newPasswordConfirm)) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            // Block disallowed characters
            for (String password : new String[]{args[0], args[1]}) {
                PasswordValidator.ValidationResult result = PasswordValidator.validate(password, plugin.getConfig(), plugin.getLogger());

                switch (result) {
                    case INVALID_CHARACTERS:
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.validation.invalid-characters"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                        return true;
                    case ONLY_LETTERS_FORBIDDEN:
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.validation.only-letters-forbidden"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                        return true;
                    case ONLY_DIGITS_FORBIDDEN:
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.validation.only-digits-forbidden"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                        return true;
                    default:
                        break;
                }
            }

            int min = plugin.getConfig().getInt("features.password.min-password-length", 6);
            min = Math.max(5, min);

            int max = plugin.getConfig().getInt("features.password.max-password-length", 24);
            max = Math.min(32, max);

            if (min > max) {
                min = 6;
                max = 24;
            }

            if (newPassword.length() < min) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-short").replace("%min%", String.valueOf(min)));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            if (newPassword.length() > max) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-long").replace("%max%", String.valueOf(max)));
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                return true;
            }

            plugin.getSchedulerManager().runAsync(() -> {
                PasswordHasher hasher = plugin.getPasswordManager().getPasswordHasher();
                PasswordHasher.VerificationResult result = hasher.verifyPassword(playerUUID, oldPassword, currentPassword);

                switch (result.status()) {
                    case SUCCESS:
                        // Generate hash for new password with UUID passing
                        PasswordHasher.HashingResult hashResult = hasher.hashPassword(playerUUID, newPassword);

                        switch (hashResult.status()) {
                            case SUCCESS:
                                String newHashPassword = hashResult.hash();
                                plugin.getPasswordManager().savePassword(playerUUID, newHashPassword);

                                plugin.getSchedulerManager().runSync(() -> {
                                    if (plugin.getLoginSystem().getLoggedIn().contains(playerUUID)) {
                                        p.kickPlayer(plugin.getLanguageManager().getMessage("password.changed-kick"));
                                    } else {
                                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.changed"));
                                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                                    }

                                    plugin.getLogManager().log("Player " + p.getName() + " changed his password");
                                });
                                break;

                            case RATE_LIMITED_SERVER:
                                plugin.getSchedulerManager().runSync(() -> {
                                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
                                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                                });
                                break;

                            case RATE_LIMITED_PLAYER:
                                long seconds = hashResult.remainingSeconds();
                                plugin.getSchedulerManager().runSync(() -> {
                                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                                            .replace("%time%", TimeUtils.formatTime(seconds)));
                                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                                });
                                break;

                            case ERROR:
                            default:
                                plugin.getSchedulerManager().runSync(() -> {
                                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
                                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                                });
                                break;
                        }
                        break;

                    case INVALID_PASSWORD:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.WRONG_PASSWORD);
                            plugin.getIpTrustManager().addTrustScore(ip, plugin.getIpTrustManager().getFailedPasswordPoints());

                            plugin.getAttemptManager().checkCrime(p, "Password");
                        });
                        break;

                    case RATE_LIMITED_SERVER:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;

                    case RATE_LIMITED_PLAYER:
                        long seconds = result.remainingSeconds();
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                                    .replace("%time%", TimeUtils.formatTime(seconds)));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;

                    case ERROR:
                    default:
                        plugin.getSchedulerManager().runSync(() -> {
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
                            plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        });
                        break;
                }
            });

            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("zmienhaslo") || cmd.equalsIgnoreCase("changepassword")) {
            if (args.length == 1) {
                hints.add("<old password>");
            }
            if (args.length == 2) {
                hints.add("<new password>");
            }
            if (args.length == 3) {
                hints.add("<repeat new password>");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}