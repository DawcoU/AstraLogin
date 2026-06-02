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
            long banMillis = LoginUtils.parseTime(timeStr, 300000L); // 300000L to 5 minut jako default
            plugin.getIPManager().banIPWithMillis(ip, banMillis, "PASSWORD");

            // Ban za próbę włamania / wielokrotne złe hasło
            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was IP banned for " + timeStr + ". Reason: Too many failed login attempts");

            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts-ban")
                    .replace("%time%", timeStr);
            p.kick(net.kyori.adventure.text.Component.text(msg));
            return;
        }
        // 2. POTEM KICK (wyrzuca równe max i każdy błąd marginesu aż do bana)
        if (aktualne >= max) {
            int remaining = threshold - aktualne;

            // Kick za błędne hasło (wskazujemy ile prób zostało do całkowitego bana)
            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was kicked for incorrect password. Attempts: " + aktualne + "/" + threshold + " until IP ban");

            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts")
                    .replace("%remaining%", String.valueOf(remaining));

            p.kick(net.kyori.adventure.text.Component.text(msg));
            return;
        }
    }

    // Call this method in your LoginListener when password is correct!
    public void clearAttempts(UUID uuid) {
        proby.remove(uuid);
    }
}