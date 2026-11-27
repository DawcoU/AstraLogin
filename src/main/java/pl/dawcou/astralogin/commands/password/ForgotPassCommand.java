package pl.dawcou.astralogin.commands.password;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.password.PasswordHasher;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.system.SoundManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Objects;
import java.util.UUID;

public final class ForgotPassCommand implements CommandExecutor {

    private final AstraLogin plugin;

    public ForgotPassCommand(AstraLogin plugin) {
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

        if (args.length != 1) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.usage"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();
        String pin = args[0];
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        String hashedPIN = plugin.getPinManager().getPIN(playerUUID);

        if (hashedPIN == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.no-has-pin"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            PasswordHasher.VerificationResult result = plugin.getPasswordManager()
                    .getPasswordHasher()
                    .verifyPassword(playerUUID, pin, hashedPIN);

            switch (result.status()) {
                case SUCCESS:
                    plugin.getSchedulerManager().runSync(() ->
                            player.kickPlayer(plugin.getLanguageManager()
                                    .getMessage("forgot-password.player.kick"))
                    );

                    plugin.getPasswordManager().deletePassword(playerUUID);
                    plugin.getLogManager().log("Player " + playerName + " reset his password using PIN");
                    break;

                case INVALID_PASSWORD:
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("forgot-password.wrong-pin"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.WRONG_PASSWORD);

                        plugin.getIpTrustManager().changeTrustScore(
                                ip,
                                plugin.getIpTrustManager().getFailedPasswordPoints()
                        );
                        plugin.getAttemptManager().addTrial(player, AttemptType.PIN);
                    });
                    break;

                case RATE_LIMITED_SERVER:
                    plugin.getSchedulerManager().runSync(() -> {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("login.server-busy"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    });
                    break;

                case RATE_LIMITED_PLAYER:
                    long seconds = result.remainingSeconds();
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
}
