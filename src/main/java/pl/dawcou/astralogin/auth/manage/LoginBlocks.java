package pl.dawcou.astralogin.auth.manage;

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
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;

import java.util.UUID;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;

    public LoginBlocks(AstraLogin plugin) {
        this.plugin = plugin;
        loginSystem = plugin.getLoginSystem();
    }

    // Helper ułatwiający czytelność i optymalizację
    private boolean isNotAuthenticated(UUID uuid) {
        return !loginSystem.getLoggedIn().contains(uuid) || loginSystem.isWaitingFor2FA(uuid);
    }

    private void sendBlockedMessage(Player p, UUID uuid) {
        p.sendMessage(plugin.getLanguageManager().getWithPrefix(
                loginSystem.isWaitingFor2FA(uuid) ? "twofactor.required" : "general.blocked-action"
        ));
    }

    /*
     * ==========================================
     * 1. KOMENDY (HIGHEST - Priorytet dla bezpieczeństwa)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!isNotAuthenticated(uuid)) return;

        String message = e.getMessage().toLowerCase().trim();
        String cmd = message.split(" ")[0];

        // Normalizacja pod kątem prefiksów np. /minecraft:tell -> /tell
        if (cmd.contains(":")) {
            cmd = "/" + cmd.substring(cmd.indexOf(":") + 1);
        }

        // 1. Jeśli czeka na 2FA
        if (loginSystem.isWaitingFor2FA(uuid)) {
            if (cmd.equals("/2fa") || cmd.startsWith("/2fa ") ||
                    cmd.equals("/tfa") || cmd.startsWith("/tfa ") ||
                    cmd.equals("/auth") || cmd.startsWith("/auth ")) {
                return;
            }
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));
            return;
        }

        // 2. Jeśli jest całkowicie niezalogowany
        if (cmd.equals("/login") || cmd.startsWith("/login ") ||
                cmd.equals("/l") || cmd.startsWith("/l ") ||
                cmd.equals("/zaloguj") || cmd.startsWith("/zaloguj ") ||
                cmd.equals("/register") || cmd.startsWith("/register ") ||
                cmd.equals("/reg") || cmd.startsWith("/reg ") ||
                cmd.equals("/zarejestruj") || cmd.startsWith("/zarejestruj ") ||
                cmd.equals("/zmienhaslo") || cmd.startsWith("/zmienhaslo ") ||
                cmd.equals("/changepassword") || cmd.startsWith("/changepassword ") ||
                cmd.equals("/niepamietamhasla") || cmd.startsWith("/niepamietamhasla ") ||
                cmd.equals("/forgotpassword") || cmd.startsWith("/forgotpassword ") ||
                cmd.equals("/forgotpass") || cmd.startsWith("/forgotpass ")) {
            return;
        }

        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("general.blocked-action"));
    }

    /*
     * ==========================================
     * 2. CZAT, RUCH I PORTALE (LOWEST)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (isNotAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getX() == e.getTo().getX() &&
                e.getFrom().getY() == e.getTo().getY() &&
                e.getFrom().getZ() == e.getTo().getZ()) {
            return; // Obracanie głową dozwolone
        }

        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(PlayerPortalEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 3. BLOKI I INTERAKCJE (LOWEST)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isNotAuthenticated(p.getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage(p, p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isNotAuthenticated(p.getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage(p, p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isNotAuthenticated(p.getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage(p, p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 4. WALKA, OBRAŻENIA I PROJECTILE (LOWEST)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (isNotAuthenticated(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            if (isNotAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
                sendBlockedMessage(p, p.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player) {
            if (isNotAuthenticated(e.getTarget().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player) {
            Player p = (Player) e.getEntity().getShooter();
            if (isNotAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    /*
     * ==========================================
     * 5. EKWIPUNEK I PRZEDMIOTY (LOWEST)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (isNotAuthenticated(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage((Player) e.getWhoClicked(), e.getWhoClicked().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage((Player) e.getPlayer(), e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (isNotAuthenticated(p.getUniqueId())) {
            e.setCancelled(true);
            sendBlockedMessage(p, p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (isNotAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(PlayerItemHeldEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 6. FIZJOLOGIA I STANY GRACZA (LOWEST)
     * ==========================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (isNotAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}