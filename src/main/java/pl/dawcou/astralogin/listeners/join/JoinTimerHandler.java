package pl.dawcou.astralogin.listeners.join;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JoinTimerHandler {

    private final AstraLogin plugin;

    public JoinTimerHandler(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void startLoginTimer(Player p, UUID uuid, LoginSystem loginSystem) {
        if (!plugin.getConfig().getBoolean("features.timer.enabled")) {
            return;
        }

        String rawTime = plugin.getConfig().getString("features.timer.time-limit", "2 minutes");
        long parsedMillis = TimeUtils.parseTime(rawTime, 60000L);

        long clampedMillis = Math.max(40000L, Math.min(240000L, parsedMillis));

        final int maxTime = (int) (clampedMillis / 1000);
        final int[] time = {maxTime};
        boolean useBossBar = plugin.getConfig().getBoolean("features.timer.boss-bar.use", true);

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(plugin.getConfig().getString("features.timer.boss-bar.color", "RED").toUpperCase());
        } catch (IllegalArgumentException ex) { color = BossBar.Color.RED; }

        BossBar.Overlay overlay;
        try {
            overlay = BossBar.Overlay.valueOf(plugin.getConfig().getString("features.timer.boss-bar.style", "PROGRESS").toUpperCase());
        } catch (IllegalArgumentException ex) { overlay = BossBar.Overlay.PROGRESS; }

        final BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f, color, overlay);

        if (useBossBar) {
            plugin.getAdventure().player(p).showBossBar(bossBar);
        }

        plugin.getSchedulerManager().runAsyncRepeating(task -> {
            if (!p.isOnline() || (loginSystem.getLoggedIn().contains(uuid) && !loginSystem.isWaitingFor2FA(uuid))) {
                if (useBossBar) {
                    plugin.getSchedulerManager().runForEntity(p, () -> plugin.getAdventure().player(p).hideBossBar(bossBar));
                }
                task.cancel();
                return;
            }

            if (time[0] <= 0) {
                task.cancel();

                plugin.getSchedulerManager().runForEntity(p, () -> {
                    if (useBossBar) {
                        plugin.getAdventure().player(p).hideBossBar(bossBar);
                    }

                    String timeoutReason = plugin.getLanguageManager().getMessage("security.timeout");
                    p.kickPlayer(timeoutReason);
                });

                plugin.getLogManager().log("Player " + p.getName() + " Was kicked for exceeding the login timeout");
                return;
            }

            String rawMsg = plugin.getLanguageManager()
                    .getMessage("session.timer")
                    .replace("%time%", TimeUtils.formatTime(time[0]));

            Component message = LegacyComponentSerializer.legacySection().deserialize(rawMsg);

            plugin.getSchedulerManager().runForEntity(p, () -> {
                if (useBossBar) {
                    float progress = (float) time[0] / maxTime;
                    bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                    bossBar.name(message);
                } else {
                    plugin.getAdventure().player(p).sendActionBar(message);
                }
            });

            time[0]--;
        }, 0, 1, TimeUnit.SECONDS);
    }
}