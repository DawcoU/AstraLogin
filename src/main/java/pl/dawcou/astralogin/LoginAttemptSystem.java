package pl.dawcou.astralogin;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.UUID;

public class LoginAttemptSystem {

    private final HashMap<UUID, Integer> proby = new HashMap<>();
    private final AstraLogin plugin;

    public LoginAttemptSystem(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void dodajProbe(Player p) {
        int max = plugin.getConfig().getInt("features.max-login-attempts");
        int aktualne = proby.getOrDefault(p.getUniqueId(), 0) + 1;

        if (aktualne >= max) {
            resetuj(p.getUniqueId());
            p.kickPlayer(plugin.getLanguageManager().getMessage("kick-max-attempts"));
        } else {
            proby.put(p.getUniqueId(), aktualne);
        }
    }

    public void resetuj(UUID uuid) {
        proby.remove(uuid);
    }
}