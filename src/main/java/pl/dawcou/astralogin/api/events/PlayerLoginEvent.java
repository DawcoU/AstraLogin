package pl.dawcou.astralogin.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jspecify.annotations.NonNull;
import pl.dawcou.astralogin.api.AstraLoginEvent;

public class PlayerLoginEvent extends AstraLoginEvent implements Cancellable {

    private boolean cancelled;

    public PlayerLoginEvent(@NonNull Player player, @NonNull String ip) {
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