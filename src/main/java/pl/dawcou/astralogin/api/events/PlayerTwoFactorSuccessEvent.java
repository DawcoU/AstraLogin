package pl.dawcou.astralogin.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.api.AstraLoginEvent;

public class PlayerTwoFactorSuccessEvent extends AstraLoginEvent implements Cancellable {

    private boolean cancelled;

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public PlayerTwoFactorSuccessEvent(@NonNull Player player, @NonNull String ip) {
        super(player, ip);
        this.cancelled = false;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}