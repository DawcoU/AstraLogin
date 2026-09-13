package pl.dawcou.astralogin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import pl.dawcou.astralogin.AstraLogin;

public class TechnicalListeners implements Listener {

    private final AstraLogin plugin;

    public TechnicalListeners(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Odświeżamy lokacje w cache, żeby powiązać je z załadowanym obiektem World
        if (plugin.getSpawnManager() != null) {
            plugin.getSpawnManager().reload();
        }
    }
}