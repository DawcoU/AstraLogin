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

    public void dodajProbe(Player p, String type) {
        String ip = p.getAddress().getAddress().getHostAddress();
        String path = "features.attempts.";

        int max = plugin.getConfig().getInt(path + "max", 3);
        int margin = plugin.getConfig().getInt(path + "margin", 2);
        int threshold = max + margin;
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "5 minutes");

        int aktualne = proby.getOrDefault(p.getUniqueId(), 0) + 1;
        proby.put(p.getUniqueId(), aktualne);

        if (aktualne >= threshold) {
            clearAttempts(p.getUniqueId());
            long banMillis = LoginUtils.parseTime(timeStr, 300000L);
            plugin.getIPManager().banIPWithMillis(ip, banMillis, type); // Używamy typu (PASSWORD lub 2FA)

            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was IP banned. Reason: Too many failed " + type + " attempts");

            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts-ban")
                    .replace("%time%", timeStr);
            p.kick(net.kyori.adventure.text.Component.text(msg));
            return;
        }

        if (aktualne >= max) {
            int remaining = threshold - aktualne;
            plugin.getLogManager().log("Player " + p.getName() + " (" + ip + ") was kicked for incorrect " + type + ". Attempts: " + aktualne + "/" + threshold);

            String msg = plugin.getLanguageManager().getMessage("kick-max-attempts")
                    .replace("%remaining%", String.valueOf(remaining));
            p.kick(net.kyori.adventure.text.Component.text(msg));
            return;
        }
    }

    public void clearAttempts(UUID uuid) {
        proby.remove(uuid);
    }
}