package pl.dawcou.astralogin.auth.security.attempt;

import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.captcha.mode.gui.GuiCaptchaHandler;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AttemptManager {

    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> captchaAttempts = new ConcurrentHashMap<>();

    private final AstraLogin plugin;

    public AttemptManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void addTrial(Player p, AttemptType type) {
        if (!plugin.getConfig().getBoolean("security.attempts.enabled", true)) {
            return;
        }

        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        int max = plugin.getConfig().getInt("security.attempts.max", 3);
        int margin = plugin.getConfig().getInt("security.attempts.margin", 2);
        int threshold = max + margin;
        String timeStr = plugin.getConfig().getString("security.attempts.tempban-time", "15 minutes");

        int current = attempts.merge(p.getUniqueId(), 1, Integer::sum);

        if (current >= threshold) {
            clearAttempts(p.getUniqueId());
            long banMillis = TimeUtils.parseTime(timeStr, 300000L);
            plugin.getIPManager().banIPWithMillis(ip, banMillis, type, p.getUniqueId());

            plugin.getSuspicionManager().incrementSuspicion(p.getUniqueId());
            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was IP banned. Reason: Too many failed " + type + " attempts");

            String msg = plugin.getLanguageManager().getMessage("security.max-attempts-ban")
                    .replace("%time%", timeStr);
            p.kickPlayer(msg);
            return;
        }

        if (current >= max) {
            int remaining = threshold - current;
            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was kicked for incorrect " + type + ". Attempts: " + current + "/" + threshold);

            String msg = plugin.getLanguageManager().getMessage("security.max-attempts")
                    .replace("%remaining%", String.valueOf(remaining));
            p.kickPlayer(msg);
            return;
        }
    }

    public void addCaptchaTrial(Player p, AttemptType type) {
        UUID uuid = p.getUniqueId();

        boolean bansEnabled = plugin.getConfig().getBoolean("security.captcha.attempts.ban-enabled", true);
        int maxFails = Math.min(Math.max(plugin.getConfig().getInt("security.captcha.max-attempts", 2), 1), 5);
        int margin = plugin.getConfig().getInt("security.captcha.attempts.margin", 2);
        int threshold = maxFails + margin;

        String timeStr = plugin.getConfig().getString("security.captcha.attempts.tempban-time", "15 minutes");

        int current = captchaAttempts.merge(uuid, 1, Integer::sum);
        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();

        // 1. Sprawdzamy próg bana (max + margin)
        if (bansEnabled && current >= threshold) {
            captchaAttempts.remove(uuid); // Przy banie czyścimy próby
            GuiCaptchaHandler.removeSession(p);

            long banMillis = TimeUtils.parseTime(timeStr, 900000L);
            plugin.getIPManager().banIPWithMillis(ip, banMillis, type, p.getUniqueId());

            String banMsg = plugin.getLanguageManager().getMessage("captcha.max-attempts-ban")
                    .replace("%time%", timeStr)
                    .replace("%attempts%", String.valueOf(current))
                    .replace("%max%", String.valueOf(threshold));
            p.kickPlayer(banMsg);

            plugin.getSuspicionManager().incrementSuspicion(uuid);
            plugin.getIpTrustManager().changeTrustScore(
                    ip,
                    plugin.getIpTrustManager().getCaptchaFailedPoints()
            );

            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was IP banned for exceeding max captcha attempts with margin (" + current + "/" + threshold + ")");
            return;
        }

        // 2. Zwykły kick po osiągnięciu maxFails z podaniem pozostałych prób do bana
        if (current >= maxFails) {
            GuiCaptchaHandler.removeSession(p);

            int remaining = threshold - current;
            String kickMsg = plugin.getLanguageManager().getMessage("captcha.max-attempts")
                    .replace("%remaining%", String.valueOf(remaining))
                    .replace("%attempts%", String.valueOf(current))
                    .replace("%max%", String.valueOf(maxFails));
            p.kickPlayer(kickMsg);

            plugin.getIpTrustManager().changeTrustScore(
                    ip,
                    plugin.getIpTrustManager().getCaptchaFailedPoints()
            );

            plugin.getLogManager().log("Player " + p.getName() + " was kicked for exceeding max captcha attempts (" + current + "/" + maxFails + ")");
        } else {
            // Wiadomość o błędnej próbie na chacie
            if (type == AttemptType.CAPTCHA_GUI) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(p, "captcha.failed")
                        .replace("%attempts%", String.valueOf(current))
                        .replace("%max%", String.valueOf(maxFails)));
            } else if (type == AttemptType.CAPTCHA_MAP) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(p, "captcha.map.wrong-code")
                        .replace("%attempts%", String.valueOf(current))
                        .replace("%max%", String.valueOf(maxFails)));
            } else if (type == AttemptType.CAPTCHA_CHAT) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(p, "captcha.chat.wrong-answer")
                        .replace("%attempts%", String.valueOf(current))
                        .replace("%max%", String.valueOf(maxFails)));
            }
        }
    }

    public void checkCrime(Player p, AttemptType type) {
        if (plugin.getConfig().getInt("security.attempts.max", 3) > 0) {
            addTrial(p, type);
        }
    }

    public void clearAttempts(UUID uuid) { attempts.remove(uuid); }

    public void clearCaptchaAttempts(UUID uuid) { captchaAttempts.remove(uuid); }
}