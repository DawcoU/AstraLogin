package pl.dawcou.astralogin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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

import static pl.dawcou.astralogin.AstraLogin.PREFIX;

public class LoginBlocks implements Listener {

    private final AstraLogin plugin;
    private final LoginSystem loginSystem;
    private final InventoryStorage storage;

    // Musisz dodać 'InventoryStorage storage' tutaj w nawiasie:
    public LoginBlocks(AstraLogin plugin, LoginSystem loginSystem, InventoryStorage storage) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
        this.storage = storage; // I przypisać go tutaj!
    }

    private String c(String path) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) return "§cMissing message: " + path;
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (loginSystem.getZalogowani().contains(uuid) && plugin.getConfig().getBoolean("features.session-enabled")) {
            loginSystem.getSesje().put(uuid, System.currentTimeMillis());
            loginSystem.getSesjeIP().put(uuid, p.getAddress().getAddress().getHostAddress());
        }

        loginSystem.getZalogowani().remove(uuid);
        loginSystem.getAttemptSystem().resetuj(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        // Update Checker
        if (plugin.getConfig().getBoolean("check-updates", true) && p.hasPermission("astralogin.update")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.sendMessage("");
                            p.sendMessage("§7------------ " + PREFIX + " §7------------");
                            p.sendMessage("§eDostępna jest nowa wersja AstraLogin: §fv" + version);
                            p.sendMessage("§aPobierz: §f§nhttps://modrinth.com/plugin/astralogin/version/" + version);
                            p.sendMessage("§7----------------------------------------------");
                            p.sendMessage("");
                        });
                    }
                });
            });
        }

        loginSystem.getZalogowani().remove(uuid);
        loginSystem.getStorage().hide(p);

        // SESJA
        if (plugin.getConfig().getBoolean("features.session-enabled")) {
            if (loginSystem.getSesje().containsKey(uuid) && loginSystem.getSesjeIP().get(uuid).equals(currentIP)) {
                long lastLogout = loginSystem.getSesje().get(uuid);
                long sessionLimit = plugin.getConfig().getLong("features.session-time") * 60 * 1000;

                if (System.currentTimeMillis() - lastLogout <= sessionLimit) {
                    loginSystem.finishLogin(p); // Ta metoda musi być public w LoginSystem!
                    p.sendMessage(PREFIX + " " + c("messages.session-restored"));
                    storage.restore(p);
                    loginSystem.getSesje().remove(uuid);
                    loginSystem.getSesjeIP().remove(uuid);
                    return;
                }
            }
        }

        if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (loginSystem.getData().maHaslo(uuid.toString())) {
            p.sendMessage(PREFIX + " " + c("messages.reminder-login"));
        } else {
            p.sendMessage(PREFIX + " " + c("messages.reminder-register"));
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
                        p.kickPlayer(c("messages.kick-timeout"));
                        this.cancel();
                        return;
                    }
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(c("messages.actionbar-timer").replace("%time%", String.valueOf(time[0]))));
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
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, c("messages.already-online"));
            return;
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            String zapisaneIP = loginSystem.getIpData().getIP(uuid.toString());
            int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
            int ileKont = loginSystem.getIpData().getIloscKontByIP(currentIP);

            if (!currentIP.equals(zapisaneIP) && ileKont >= limit) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, c("messages.anti-multiaccount-kick"));
            }
        }
    }

    // --- BLOKADY (ROZBITA LOGIKA) ---

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + " " + c("messages.reminder-login"));
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
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDmg(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (!loginSystem.getZalogowani().contains(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInv(InventoryClickEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!loginSystem.getZalogowani().contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            if (!loginSystem.getZalogowani().contains(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }
}