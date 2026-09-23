package pl.dawcou.astralogin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.dawcou.astralogin.AstraLogin;

import java.util.UUID;

public class TechnicalListeners implements Listener {

    private final AstraLogin plugin;

    public TechnicalListeners(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        // Odświeżamy lokacje w cache, żeby powiązać je z załadowanym obiektem World
        if (plugin.getSpawnManager() != null) {
            plugin.getSpawnManager().reload();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Nadanie efektu blindness graczowi po śmierci w czasie logowania
        if (!plugin.getLoginSystem().getLoggedIn().contains(uuid)) {
            if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
            }
        }
    }
}