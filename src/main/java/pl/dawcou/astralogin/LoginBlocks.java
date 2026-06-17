package pl.dawcou.astralogin;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;

    public LoginBlocks(AstraLogin plugin) {
        this.plugin = plugin;
        this.loginSystem = plugin.getLoginSystem();
    }

    // --- BLOKADY (ROZBITA LOGIKA) ---

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        String message = e.getMessage().toLowerCase();
        String cmd = message.split(" ")[0];

        // 1. Jeśli jest w trakcie 2FA -> pozwalamy TYLKO na /2fa (nawet jeśli jest w getZalogowani())
        if (loginSystem.isWaitingFor2FA(uuid)) {
            if (cmd.startsWith("/2fa")) {
                return;
            }
            // Blokujemy wszystko inne dla gracza oczekującego na kod
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-required"));
            return;
        }

        // 2. Jeśli jest w pełni zalogowany i nie czeka na 2FA -> wpuszczamy wszędzie
        if (loginSystem.getZalogowani().contains(uuid)) {
            return;
        }

        // 3. Jeśli nie jest zalogowany i NIE jest w trakcie 2FA -> pozwalamy TYLKO na login/register
        if (cmd.startsWith("/login") || cmd.startsWith("/l ") || cmd.startsWith("/zaloguj") ||
                cmd.startsWith("/register") || cmd.startsWith("/reg ") || cmd.startsWith("/zarejestruj") ||
                cmd.startsWith("/zmienhaslo") || cmd.startsWith("/changepassword")) {
            return;
        }

        // Blokada reszty dla całkowicie niezalogowanych
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            // Jeśli ma hasło z sesji, ale czeka na 2FA, wysyłamy komunikat o 2FA
            if (loginSystem.isWaitingFor2FA(uuid)) {
                e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-required"));
            } else {
                e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Pobieramy dokładne współrzędne (Double)
        if (e.getFrom().getX() == e.getTo().getX() &&
                e.getFrom().getY() == e.getTo().getY() &&
                e.getFrom().getZ() == e.getTo().getZ()) {
            return; // Jeśli zmienił tylko kierunek patrzenia (myszkę), pozwól mu na to
        }

        UUID uuid = e.getPlayer().getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onDmg(EntityDamageByEntityEvent e) {
        // 1. Blokada otrzymywania obrażeń (niezalogowany/w trakcie 2FA jest nieśmiertelny)
        if (e.getEntity() instanceof Player) {
            UUID uuid = e.getEntity().getUniqueId();
            if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
                e.setCancelled(true);
                return;
            }
        }

        // 2. Blokada zadawania obrażeń (niezalogowany/w trakcie 2FA nikogo nie uderzy)
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            UUID uuid = p.getUniqueId();
            if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
                e.setCancelled(true);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
            }
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        // 3. Moby ignorują gracza bez pełnego loginu (również w trakcie 2FA)
        if (e.getTarget() instanceof Player) {
            UUID uuid = e.getTarget().getUniqueId();
            if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        Player p = (Player) e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            UUID uuid = p.getUniqueId();

            if (!loginSystem.getZalogowani().contains(uuid) || loginSystem.isWaitingFor2FA(uuid)) {
                e.setCancelled(true);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(loginSystem.isWaitingFor2FA(uuid) ? "2fa-required" : "blocked-action"));
            }
        }
    }
}