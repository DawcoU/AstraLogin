package pl.dawcou.astralogin.auth.security;

import com.comphenix.protocol.ProtocolManager;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.protocol.listeners.PacketEventsListener;
import pl.dawcou.astralogin.auth.security.premium.protocol.listeners.ProtocolLibListener;

public class BackendManager {

    private final AstraLogin plugin;
    private String activeBackend = "PROTOCOLLIB";

    public BackendManager(AstraLogin plugin) {
        this.plugin = plugin;
        initializeBackend();
    }

    private void initializeBackend() {
        String firstChoice = plugin.getConfig().getString("security.auto-login.backend", "ProtocolLib").toUpperCase();
        boolean loaded = false;

        boolean hasProtocolLib = plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        boolean hasPacketEvents = plugin.getServer().getPluginManager().getPlugin("packetevents") != null;

        String secondChoice = "PROTOCOLLIB".equals(firstChoice) ? "PACKETEVENTS" : "PROTOCOLLIB";

        if ("PACKETEVENTS".equals(firstChoice) && hasPacketEvents) {
            new PacketEventsListener(plugin);
            activeBackend = "PACKETEVENTS";
            plugin.getLogger().info("✅ Successfully loaded PacketEvents backend for premium login.");
            loaded = true;
        } else if ("PROTOCOLLIB".equals(firstChoice) && hasProtocolLib) {
            ProtocolManager protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            new ProtocolLibListener(plugin, protocolManager);
            activeBackend = "PROTOCOLLIB";
            plugin.getLogger().info("✅ Successfully loaded ProtocolLib backend for premium login.");
            loaded = true;
        } else {
            plugin.getLogger().warning("⚠️ Preferred backend (" + firstChoice + ") is missing! Attempting fallback...");

            if ("PACKETEVENTS".equals(secondChoice) && hasPacketEvents) {
                new PacketEventsListener(plugin);
                activeBackend = "PACKETEVENTS";
                plugin.getLogger().info("✅ Successfully switched and loaded PacketEvents backend.");
                loaded = true;
            } else if ("PROTOCOLLIB".equals(secondChoice) && hasProtocolLib) {
                ProtocolManager protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
                new ProtocolLibListener(plugin, protocolManager);
                activeBackend = "PROTOCOLLIB";
                plugin.getLogger().info("✅ Successfully switched and loaded ProtocolLib backend.");
                loaded = true;
            }
        }

        if (!loaded) {
            plugin.getLogger().severe("❌ Neither ProtocolLib nor PacketEvents is installed! Premium auto-login has been disabled.");
        }
    }

    public String getActiveBackend() {
        return activeBackend;
    }
}