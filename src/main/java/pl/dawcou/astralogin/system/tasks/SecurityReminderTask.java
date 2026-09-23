package pl.dawcou.astralogin.system.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.TimeUtils;
import pl.dawcou.astralogin.system.SchedulerManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SecurityReminderTask {

    private final AstraLogin plugin;
    private SchedulerManager.Task currentTask;

    public SecurityReminderTask(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        stop();

        if (!plugin.getConfig().getBoolean("security.security-reminder.enabled", true)) {
            return;
        }

        String rawTime = plugin.getConfig().getString("security.security-reminder.interval", "24 hours");
        long intervalMillis = TimeUtils.parseTime(rawTime, 86400000L);

        this.currentTask = plugin.getSchedulerManager().runAsyncRepeating(task -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                if (!plugin.getLoginSystem().getLoggedIn().contains(uuid)) {
                    continue;
                }

                boolean has2FA = plugin.getTwoFactorManager().has2FA(uuid);
                boolean hasPin = plugin.getPinManager().hasPIN(uuid);

                if (has2FA && hasPin) {
                    continue;
                }

                long lastReminder = plugin.getAccountManager().getLastSecurityReminderTime(uuid);

                if ((now - lastReminder) >= intervalMillis) {
                    if (!has2FA && !hasPin) {
                        sendReminderMessage(player, "security-reminder.no-2fa-no-pin");
                    } else if (!has2FA) {
                        sendReminderMessage(player, "security-reminder.no-2fa-has-pin");
                    } else {
                        sendReminderMessage(player, "security-reminder.has-2fa-no-pin");
                    }

                    plugin.getAccountManager().setLastSecurityReminderTime(uuid, now);
                }
            }
        }, 1L, 1L, TimeUnit.MINUTES);
    }

    /**
     * Bezpieczne zatrzymanie bieżącego zadania (wywoływane przy reloadzie oraz w onDisable).
     */
    public synchronized void stop() {
        if (currentTask != null) {
            currentTask.cancel(); // Twój interfejs Task udostępnia metodę cancel()
            currentTask = null;
        }
    }

    private void sendReminderMessage(Player player, String configPath) {
        List<String> messages = plugin.getLanguageManager().getMessageList(configPath);
        String prefix = plugin.getLanguageManager().parseToLegacy(AstraLogin.PREFIX);

        for (String msg : messages) {
            player.sendMessage(prefix + " " + msg);
        }
    }
}