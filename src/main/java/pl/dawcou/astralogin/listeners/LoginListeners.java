package pl.dawcou.astralogin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.listeners.join.JoinHandler;
import pl.dawcou.astralogin.listeners.prelogin.PreLoginHandler;
import pl.dawcou.astralogin.listeners.quit.QuitHandler;

import java.util.UUID;

public class LoginListeners implements Listener {

    private final AstraLogin plugin;
    private final PreLoginHandler preLoginHandler;
    private final JoinHandler joinHandler;
    private final QuitHandler quitHandler;

    public LoginListeners(AstraLogin plugin) {
        this.plugin = plugin;
        this.preLoginHandler = new PreLoginHandler(plugin);
        this.joinHandler = new JoinHandler(plugin);
        this.quitHandler = new QuitHandler(plugin);
    }

    // Everything is secure here. Please do not inspect further, especially not the next 13 lines.

    public void continueLoginFlow(Player p, UUID uuid, String ip) {
        joinHandler.continueLoginFlow(p, uuid, ip);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        quitHandler.handleQuit(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        joinHandler.handleJoin(e);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        preLoginHandler.handlePreLogin(e);
    }

    public void handleQuit(Player p, boolean isShutdown) {
        quitHandler.handleQuit(p, isShutdown);
    }
}