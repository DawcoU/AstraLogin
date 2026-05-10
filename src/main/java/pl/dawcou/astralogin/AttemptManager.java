package pl.dawcou.astralogin;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.UUID;

public class AttemptManager {

    private final HashMap<UUID, Integer> proby = new HashMap<>();
    private final AstraLogin plugin;

    public AttemptManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void dodajProbe(Player p) {
        String ip = p.getAddress().getAddress().getHostAddress();
        String path = "features.attempts.";

        int max = plugin.getConfig().getInt(path + "max", 3);
        int margin = plugin.getConfig().getInt(path + "margin", 2);
        int threshold = max + margin;
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "5 minutes");

        int aktualne = proby.getOrDefault(p.getUniqueId(), 0) + 1;
        proby.put(p.getUniqueId(), aktualne);

        // 1. NAJPIERW SPRAWDZAMY BANA (bo to najważniejsze)
        if (aktualne >= threshold) {
            clearAttempts(p.getUniqueId());
            long banMillis = parseTime(timeStr);
            plugin.getIPManager().banIPWithMillis(ip, banMillis, "PASSWORD");

            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts-ban")
                    .replace("%time%", timeStr);
            p.kickPlayer(msg);
            return;
        }
        // 2. POTEM KICK (wyrzuca równe max i każdy błąd marginesu aż do bana)
        if (aktualne >= max) {
            int remaining = threshold - aktualne;
            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts")
                    .replace("%remaining%", String.valueOf(remaining));

            p.kickPlayer(msg);
            return;
        }
    }

    // Call this method in your LoginListener when password is correct!
    public void clearAttempts(UUID uuid) {
        proby.remove(uuid);
    }

    public long parseTime(String input) {
        try {
            String[] parts = input.split(" ");
            long value = Long.parseLong(parts[0]);
            String unit = parts[1].toLowerCase();

            return switch (unit) {
                case "seconds", "second" -> value * 1000L;
                case "minutes", "minute" -> value * 60000L;
                case "hours", "hour" -> value * 3600000L;
                default -> value * 60000L;
            };
        } catch (Exception e) {
            return 300000L;
        }
    }
}