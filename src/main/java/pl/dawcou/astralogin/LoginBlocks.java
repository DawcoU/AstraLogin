package pl.dawcou.astralogin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;
    private final InventoryStorage storage;
    private final SpawnManager spawnManager;

    public LoginBlocks(AstraLogin plugin, LoginSystem loginSystem, InventoryStorage storage, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
        this.storage = storage;
        this.spawnManager = spawnManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String ip = p.getAddress().getAddress().getHostAddress();

        // 1. NAJPIERW ZAPISZ LOKALIZACJĘ (Zanim cokolwiek innego się stanie)
        if (loginSystem.getZalogowani().contains(uuid)) {
            spawnManager.saveLastLocation(p);
        }

        // 2. NA KOŃCU SESJE I CZYSZCZENIE
        if (loginSystem.getZalogowani().contains(uuid) && plugin.getConfig().getBoolean("features.session-enabled")) {
            loginSystem.getSesje().put(uuid, System.currentTimeMillis());
            loginSystem.getSesjeIP().put(uuid, p.getAddress().getAddress().getHostAddress());
        }

        loginSystem.getSesje().put(uuid, System.currentTimeMillis());
        loginSystem.getSesjeIP().put(uuid, ip);
        loginSystem.getZalogowani().remove(uuid);
        loginSystem.getAttemptSystem().resetuj(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();

        // Sesja
        if (plugin.getConfig().getBoolean("features.session-enabled")) {
            if (loginSystem.getSesje().containsKey(uuid)) {

                if (!plugin.getPasswordManager().maHaslo(uuid)) {
                    return;
                }

                // --- SPRAWDZANIE IP (ZABEZPIECZENIE) ---
                if (p.getAddress() == null) return; // Mały safe-check
                String currentIP = p.getAddress().getAddress().getHostAddress();
                String savedIP = loginSystem.getSesjeIP().get(uuid);

                // Jeśli IP się nie zgadza (ktoś inny próbuje wejść na sesję):
                if (savedIP == null || !savedIP.equals(currentIP)) {
                    loginSystem.getSesje().remove(uuid);
                    loginSystem.getSesjeIP().remove(uuid);
                    return; // Kończymy sprawdzanie sesji, gracz musi się zalogować
                }

                // 1. POBIERAMY TEKST Z CONFIGU (np. "5 minutes" lub "10 seconds")
                String timeRaw = plugin.getConfig().getString("features.session-time", "5 minutes").toLowerCase();
                long sessionLimitMillis;

                try {
                    String[] parts = timeRaw.split(" ");
                    long value = Long.parseLong(parts[0]);

                    if (parts.length > 1 && parts[1].startsWith("hour")) {
                        sessionLimitMillis = value * 3600000;
                    } else if (parts.length > 1 && parts[1].startsWith("second")) {
                        sessionLimitMillis = value * 1000;
                    } else {
                        sessionLimitMillis = value * 60000;
                    }
                } catch (Exception ex) {
                    sessionLimitMillis = 300000;
                    plugin.sendInvalidSessionFormatNotice();
                }

                long lastLogout = loginSystem.getSesje().get(uuid);

                // 2. SPRAWDZANIE CZASU
                if (System.currentTimeMillis() - lastLogout <= sessionLimitMillis) {
                    loginSystem.finishLogin(p);
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));

                    // TUTAJ: Czyścimy obie mapy, bo sesja została właśnie zużyta
                    loginSystem.getSesje().remove(uuid);
                    loginSystem.getSesjeIP().remove(uuid);

                    return;
                }
            }
        }

        loginSystem.getStorage().save(p);
        spawnManager.teleport(p, "before_login");
        loginSystem.getZalogowani().remove(uuid);

        // Update Checker
        if (plugin.getConfig().getBoolean("check-updates", true) && p.hasPermission("astralogin.update")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Wysyłamy powiadomienie o nowej wersji
                            plugin.sendUpdateNotice(p, version); // p to Twój obiekt Player
                        });
                    }
                });
            });
        }

        if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (loginSystem.getData().maHaslo(uuid.toString())) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("reminder-login"));
        } else {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("reminder-register"));
        }

        // Timer logowania
        if (plugin.getConfig().getBoolean("features.login-time-enabled")) {
            final int[] time = {plugin.getConfig().getInt("features.login-time-limit")};
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline() || loginSystem.getZalogowani().contains(p.getUniqueId())) {
                        this.cancel();
                        return;
                    }
                    if (time[0] <= 0) {
                        p.kickPlayer(plugin.getLanguageManager().getMessage("kick-timeout"));
                        this.cancel();
                        return;
                    }

                    String actionBarMsg = plugin.getLanguageManager().getMessage("actionbar-timer")
                            .replace("%time%", String.valueOf(time[0]));
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMsg));

                    time[0]--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        UUID uuid = e.getUniqueId();
        String currentIP = e.getAddress().getHostAddress();

        if (Bukkit.getPlayerExact(name) != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.getLanguageManager().getMessage("already-online"));
            return;
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            String zapisaneIP = loginSystem.getIpManager().getIP(uuid.toString());

            boolean isSafe = IPSecurity.isIPSafe(zapisaneIP, currentIP);

            int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
            int ileKont = loginSystem.getIpManager().getIloscKontByIP(currentIP);

            if (!isSafe && ileKont >= limit) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getLanguageManager().getMessage("anti-multiaccount-kick"));
            }
        }
    }

    // --- BLOKADY (ROZBITA LOGIKA) ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        // Jedziemy bezpośrednio z e, tak jak w Twoim przykładzie z eq!
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {

            String msg = e.getMessage().toLowerCase();

            // Biała lista, żeby mogli wpisać hasło
            if (msg.startsWith("/login") || msg.startsWith("/zaloguj") ||
                    msg.startsWith("/register") || msg.startsWith("/zarejestruj")) {
                return;
            }

            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("blocked-action"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
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