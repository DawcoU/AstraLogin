package pl.dawcou.astralogin.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

public abstract class AstraLoginEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final long timestamp;
    private final String ip;

    public AstraLoginEvent(@NonNull Player player, @NonNull String ip) {
        this.player = player;
        this.timestamp = System.currentTimeMillis();
        this.ip = ip;
    }

    public @NonNull Player getPlayer() {
        return player;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public @NonNull String getIp() {
        return ip;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NonNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}