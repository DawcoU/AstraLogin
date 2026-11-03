
package pl.dawcou.astralogin.auth.manage;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.security.captcha.mode.chat.ChatCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.gui.GuiCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.map.MapCaptchaHandler;

import java.util.UUID;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;

    public LoginBlocks(AstraLogin plugin) {
        this.plugin = plugin;
        loginSystem = plugin.getLoginSystem();
    }

    // Please remain calm. The code is secure, the binaries are innocent, and the potato is under control.

    private boolean isAuthenticated(UUID uuid) {
        return loginSystem.getLoggedIn().contains(uuid)
                && !loginSystem.isWaitingFor2FA(uuid);
    }

    private void sendBlockedMessage(Player p, UUID uuid) {
        p.sendMessage(plugin.getLanguageManager().getWithPrefix(
                loginSystem.isWaitingFor2FA(uuid) ? "twofactor.required" : "general.blocked-action"
        ));
    }

    /*
     * ==========================================
     * 1. KOMENDY
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (isAuthenticated(uuid)) return;

        // Aktywna CAPTCHA blokuje wszystkie komendy
        if (plugin.getCaptchaManager().hasCaptcha(p)) {

            e.setCancelled(true);
            p.sendMessage(
                    plugin.getLanguageManager().getWithPrefix("captcha.required")
            );
            return;
        }

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
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));
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
        p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.blocked-action"));
    }

    /*
     * ==========================================
     * 2. CZAT, RUCH I PORTALE
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!isAuthenticated(uuid)
                && !MapCaptchaHandler.hasActiveCaptcha(p)
                && !ChatCaptchaHandler.hasActiveCaptcha(p)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (isAuthenticated(e.getPlayer().getUniqueId())) return;

        Location from = e.getFrom();
        Location to = e.getTo();

        if (from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()) {

            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 3. BLOKI I INTERAKCJE
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!isAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!isAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!isAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 4. WALKA, OBRAŻENIA I PROJECTILE
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (!isAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            UUID uuid = p.getUniqueId();

            if (!isAuthenticated(uuid)) {
                e.setCancelled(true);
                sendBlockedMessage(p, uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p) {
            if (!isAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            if (!isAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    /*
     * ==========================================
     * 5. EKWIPUNEK I PRZEDMIOTY
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (!isAuthenticated(uuid) && !GuiCaptchaHandler.hasActiveCaptcha(player)) {
            e.setCancelled(true);
            sendBlockedMessage(player, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (!isAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(player, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        Player player = (Player) e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isAuthenticated(uuid) && !GuiCaptchaHandler.hasActiveCaptcha(player)) {
            e.setCancelled(true);
            sendBlockedMessage(player, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!isAuthenticated(uuid)) {
            e.setCancelled(true);
            sendBlockedMessage(p, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (!isAuthenticated(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /*
     * ==========================================
     * 6. FIZJOLOGIA I STANY GRACZA
     * ==========================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (!isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
