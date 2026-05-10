package pl.dawcou.astralogin;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;

    public LoginBlocks(AstraLogin plugin) {
        this.plugin = plugin;
        // Ale ze środka pluginu wyciąga sobie LoginSystem!
        this.loginSystem = plugin.getLoginSystem();
    }

    // --- BLOKADY (ROZBITA LOGIKA) ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {

            // Wyciągamy samą komendę (pierwsze słowo)
            String cmd = e.getMessage().split(" ")[0].toLowerCase();

            if (cmd.equals("/login") || cmd.equals("/zaloguj") ||
                    cmd.equals("/register") || cmd.equals("/zarejestruj")) {
                return;
            }

            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Sprawdzamy, czy gracz faktycznie zmienił blok (X, Y lub Z)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
                e.getFrom().getBlockZ() == e.getTo().getBlockZ() &&
                e.getFrom().getBlockY() == e.getTo().getBlockY()) {
            return;
        }

        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));

        }
    }

    @EventHandler
    public void onDmg(EntityDamageByEntityEvent e) {
        // 1. Blokada otrzymywania obrażeń (niezalogowany jest nieśmiertelny)
        if (e.getEntity() instanceof Player) {
            if (!loginSystem.getZalogowani().contains(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }

        // 2. Blokada zadawania obrażeń (niezalogowany nikogo nie uderzy)
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            if (!loginSystem.getZalogowani().contains(e.getDamager().getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
            }
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        // 3. Moby ignorują gracza bez loginu
        if (e.getTarget() instanceof Player) {
            if (!loginSystem.getZalogowani().contains(e.getTarget().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        // Sprawdzamy UUID przez getWhoClicked()
        if (!loginSystem.getZalogowani().contains(e.getWhoClicked().getUniqueId())) {

            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();

            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        Player p = (Player) e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();

            if (!loginSystem.getZalogowani().contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
            }
        }
    }
}