package pl.dawcou.astralogin.commands.password;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.password.PasswordHasher;
import pl.dawcou.astralogin.auth.password.PasswordValidator;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.system.SoundManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChangePassCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    public ChangePassCommand(AstraLogin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(
            @NonNull CommandSender sender,
            @NonNull Command command,
            @NonNull String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        if (args.length != 3) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.change-usage"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();
        String oldPassword = args[0];
        String newPassword = args[1];
        String newPasswordConfirm = args[2];

        if (oldPassword.equals(newPassword)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.identical"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        String currentPassword = plugin.getPasswordManager().getPassword(playerUUID);
        if (currentPassword == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return true;
        }

        if (!newPassword.equals(newPasswordConfirm)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.not-match"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return true;
        }

        // Block disallowed characters.
        for (String password : new String[]{args[0], args[1]}) {
            PasswordValidator.ValidationResult validationResult = PasswordValidator.validate(
                    password,
                    plugin.getConfig(),
                    plugin.getLogger()
            );

            switch (validationResult) {
                case INVALID_CHARACTERS:
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("password.validation.invalid-characters"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                    return true;

                case ONLY_LETTERS_FORBIDDEN:
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("password.validation.only-letters-forbidden"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
                    return true;

                case ONLY_DIGITS_FORBIDDEN:
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("password.validation.only-digits-forbidden"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
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
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-short")
                    .replace("%min%", String.valueOf(min)));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return true;
        }

        if (newPassword.length() > max) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.too-long")
                    .replace("%max%", String.valueOf(max)));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.INVALID_PASSWORD_FORMAT);
            return true;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            PasswordHasher hasher = plugin.getPasswordManager().getPasswordHasher();
            PasswordHasher.VerificationResult verificationResult =
                    hasher.verifyPassword(playerUUID, oldPassword, currentPassword);

            switch (verificationResult.status()) {
                case SUCCESS:
                    PasswordHasher.HashingResult hashResult = hasher.hashPassword(newPassword);

                    switch (hashResult.status()) {
                        case SUCCESS:
                            String newHashPassword = hashResult.hash();
                            plugin.getPasswordManager().savePassword(playerUUID, newHashPassword);

                            plugin.getSchedulerManager().runSync(() -> {
                                if (plugin.getLoginSystem().isLoggedIn(playerUUID)) {
                                    player.kickPlayer(plugin.getLanguageManager()
                                            .getMessage("password.changed-kick"));
                                } else {
                                    player.sendMessage(plugin.getLanguageManager()
                                            .getWithPrefix("password.changed"));
                                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
                                }

                                plugin.getLogManager().log("Player " + playerName + " changed his password");
                            });
                            break;

                        case RATE_LIMITED_SERVER:
                            plugin.getSchedulerManager().runSync(() -> {
                                player.sendMessage(plugin.getLanguageManager()
                                        .getWithPrefix("login.server-busy"));
                                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                            });
                            break;

                        case RATE_LIMITED_PLAYER:
                            long hashSeconds = hashResult.remainingSeconds();
                            plugin.getSchedulerManager().runSync(() -> {
                                player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                                        .replace("%time%", TimeUtils.formatTime(hashSeconds)));
                                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                            });
                            break;

                        case ERROR:
                        default:
                            plugin.getSchedulerManager().runSync(() -> {
                                player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
                                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                            });
                            break;
                    }
                    break;

                case INVALID_PASSWORD:
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.wrong-old"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.WRONG_PASSWORD);
                        plugin.getIpTrustManager().changeTrustScore(
                                ip,
                                plugin.getIpTrustManager().getFailedPasswordPoints()
                        );
                        plugin.getAttemptManager().addTrial(player, AttemptType.PASSWORD);
                    });
                    break;

                case RATE_LIMITED_SERVER:
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    });
                    break;

                case RATE_LIMITED_PLAYER:
                    long seconds = verificationResult.remainingSeconds();
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.rate-limit")
                                .replace("%time%", TimeUtils.formatTime(seconds)));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    });
                    break;

                case ERROR:
                default:
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("password.error"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    });
                    break;
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NonNull CommandSender sender,
            @NonNull Command command,
            @NonNull String alias,
            String[] args
    ) {
        List<String> hints = new ArrayList<>();

        if (args.length == 1) {
            hints.add("<old password>");
        } else if (args.length == 2) {
            hints.add("<new password>");
        } else if (args.length == 3) {
            hints.add("<repeat new password>");
        }

        String lastArg = args[args.length - 1].toLowerCase();

        return hints.stream()
                .filter(hint -> hint.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
