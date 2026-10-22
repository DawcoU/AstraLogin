package pl.dawcou.astralogin.auth.security;

import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AttemptManager {

    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final AstraLogin plugin;

    public AttemptManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void addTrial(Player p, String type) {
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
            plugin.getIPManager().banIPWithMillis(ip, banMillis, type); // Używamy typu (PASSWORD lub 2FA)

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

    public void checkCrime(Player p, String type) {
        if (plugin.getConfig().getInt("security.attempts.max", 3) > 0) {
            addTrial(p, type);
        }
    }

    public void clearAttempts(UUID uuid) {
        attempts.remove(uuid);
    }

    public void unregisterCache(UUID uuid) {
        attempts.remove(uuid);
    }
}