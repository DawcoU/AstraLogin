package pl.dawcou.astralogin.auth.security.attempts;

import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.TimeUtils;

import java.util.HashMap;
import java.util.UUID;

public class AttemptManager {

    private final HashMap<UUID, Integer> attempts = new HashMap<>();
    private final AstraLogin plugin;

    public AttemptManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void addTrial(Player p, String type) {
        if (!plugin.getConfig().getBoolean("security.attempts.enabled", true)) {
            return;
        }

        String ip = p.getAddress().getAddress().getHostAddress();

        int max = plugin.getConfig().getInt("security.attempts.max", 3);
        int margin = plugin.getConfig().getInt("security.attempts.margin", 2);
        int threshold = max + margin;
        String timeStr = plugin.getConfig().getString("security.attempts.tempban-time", "15 minutes");

        int current = attempts.getOrDefault(p.getUniqueId(), 0) + 1;
        attempts.put(p.getUniqueId(), current);

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

    public void unregisterCache(String oldUUIDStr) {
        try {
            if (oldUUIDStr != null) {
                UUID oldUUID = UUID.fromString(oldUUIDStr);
                attempts.remove(oldUUID);
            }
        } catch (IllegalArgumentException ignored) {}
    }
}