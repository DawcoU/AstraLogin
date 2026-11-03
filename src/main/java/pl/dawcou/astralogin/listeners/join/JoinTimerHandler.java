package pl.dawcou.astralogin.listeners.join;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Objects;
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

        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        String rawTime = plugin.getConfig().getString("features.timer.time-limit", "2 minutes");
        long parsedMillis = TimeUtils.parseTime(rawTime, 60000L);
        long clampedMillis = Math.max(40000L, Math.min(240000L, parsedMillis));

        final int maxTime = (int) (clampedMillis / 1000);
        final int[] time = {maxTime};
        boolean useBossBar = plugin.getConfig().getBoolean("features.timer.boss-bar.use", true);

        BossBar.Color color = getLoginBossBarColor();
        BossBar.Overlay overlay = getLoginBossBarOverlay();

        final BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f, color, overlay);

        if (useBossBar) {
            plugin.getAdventure().player(p).showBossBar(bossBar);
        }

        plugin.getSchedulerManager().runAsyncRepeating(task -> {
            if (!p.isOnline() || (loginSystem.getLoggedIn().contains(uuid) && !loginSystem.isWaitingFor2FA(uuid))) {
                hideBossBar(p, bossBar, useBossBar);
                task.cancel();
                return;
            }

            if (time[0] <= 0) {
                task.cancel();
                hideBossBar(p, bossBar, useBossBar);

                plugin.getSchedulerManager().runForEntity(p, () -> {
                    String timeoutReason = plugin.getLanguageManager().getMessage("security.timeout");
                    p.kickPlayer(timeoutReason);
                });

                plugin.getIpTrustManager().changeTrustScore(
                        ip,
                        plugin.getIpTrustManager().getLoginTimeoutPoints()
                );

                plugin.getLogManager().log("Player " + p.getName() + " was kicked for exceeding the login timeout");
                return;
            }

            String rawMsg = plugin.getLanguageManager()
                    .getMessage("session.timer")
                    .replace("%time%", TimeUtils.formatTime(time[0]));

            Component message = LegacyComponentSerializer.legacySection().deserialize(rawMsg);

            updateBossBarOrActionBar(p, bossBar, message, useBossBar, (float) time[0] / maxTime);

            time[0]--;
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Osobny timer dedykowany do rozwiązywania captchy (pobierający dane z sekcji captcha.timer).
     */
    public void startCaptchaTimer(Player p) {
        if (!plugin.getConfig().getBoolean("security.captcha.timer.enabled", true)) {
            return;
        }

        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        String rawTime = plugin.getConfig().getString("security.captcha.timer.time-limit", "30 seconds");
        long parsedMillis = TimeUtils.parseTime(rawTime, 30000L);
        long clampedMillis = Math.max(10000L, Math.min(300000L, parsedMillis));

        final int maxTime = (int) (clampedMillis / 1000);
        final int[] time = {maxTime};
        boolean useBossBar = plugin.getConfig().getBoolean("security.captcha.timer.boss-bar.use", true);

        BossBar.Color color = getCaptchaBossBarColor();
        BossBar.Overlay overlay = getCaptchaBossBarOverlay();

        final BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f, color, overlay);

        if (useBossBar) {
            plugin.getAdventure().player(p).showBossBar(bossBar);
        }

        plugin.getSchedulerManager().runAsyncRepeating(task -> {
            // Timer kończy się, gdy gracz wyjdzie lub gdy sesja captchy zostanie zakończona
            if (!p.isOnline() || !plugin.getCaptchaManager().hasCaptcha(p)) {
                hideBossBar(p, bossBar, useBossBar);
                task.cancel();
                return;
            }

            if (time[0] <= 0) {
                task.cancel();
                hideBossBar(p, bossBar, useBossBar);

                plugin.getSchedulerManager().runForEntity(p, () -> {
                    String timeoutReason = plugin.getLanguageManager().getMessage("captcha.timeout");
                    p.kickPlayer(timeoutReason);
                });

                plugin.getIpTrustManager().changeTrustScore(
                        ip,
                        plugin.getIpTrustManager().getCaptchaTimeoutPoints()
                );

                plugin.getLogManager().log("Player " + p.getName() + " was kicked for exceeding the captcha timeout");
                return;
            }

            String rawMsg = plugin.getLanguageManager()
                    .getMessage("captcha.timer")
                    .replace("%time%", TimeUtils.formatTime(time[0]));

            Component message = LegacyComponentSerializer.legacySection().deserialize(rawMsg);

            updateBossBarOrActionBar(p, bossBar, message, useBossBar, (float) time[0] / maxTime);

            time[0]--;
        }, 0, 1, TimeUnit.SECONDS);
    }

    private BossBar.Color getLoginBossBarColor() {
        try {
            return BossBar.Color.valueOf(plugin.getConfig().getString("features.timer.boss-bar.color", "RED").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay getLoginBossBarOverlay() {
        try {
            return BossBar.Overlay.valueOf(plugin.getConfig().getString("features.timer.boss-bar.style", "PROGRESS").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private BossBar.Color getCaptchaBossBarColor() {
        try {
            return BossBar.Color.valueOf(plugin.getConfig().getString("security.captcha.timer.boss-bar.color", "GREEN").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.GREEN;
        }
    }

    private BossBar.Overlay getCaptchaBossBarOverlay() {
        try {
            return BossBar.Overlay.valueOf(plugin.getConfig().getString("security.captcha.timer.boss-bar.style", "PROGRESS").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void hideBossBar(Player p, BossBar bossBar, boolean useBossBar) {
        if (useBossBar) {
            plugin.getSchedulerManager().runForEntity(p, () -> plugin.getAdventure().player(p).hideBossBar(bossBar));
        }
    }

    private void updateBossBarOrActionBar(Player p, BossBar bossBar, Component message, boolean useBossBar, float progress) {
        plugin.getSchedulerManager().runForEntity(p, () -> {
            if (useBossBar) {
                bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                bossBar.name(message);
            } else {
                plugin.getAdventure().player(p).sendActionBar(message);
            }
        });
    }
}