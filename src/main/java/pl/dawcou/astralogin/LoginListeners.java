package pl.dawcou.astralogin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class LoginListeners implements Listener {


    private final AstraLogin plugin;
    private final LoginSystem loginSystem;
    private final SpawnManager spawnManager;

    public LoginListeners(AstraLogin plugin) {
        this.plugin = plugin;
        this.loginSystem = plugin.getLoginSystem();
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // 1. NAJPIERW ZAPISZ LOKALIZACJĘ
        if (loginSystem.getZalogowani().contains(uuid)) {
            spawnManager.saveLastLocation(p);

            if (plugin.getConfig().getBoolean("features.session-enabled")) {
                loginSystem.getSesje().put(uuid, System.currentTimeMillis());
                loginSystem.getSesjeIP().put(uuid, p.getAddress().getAddress().getHostAddress());
            }
        }

        loginSystem.getZalogowani().remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Update Checker
        if (plugin.getConfig().getBoolean("check-updates", true) && p.hasPermission("astralogin.update")) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        // Powrót do wątku gracza (Sync)
                        p.getScheduler().run(plugin, stask -> {
                            plugin.getNoticeManager().sendUpdateNotice(p, version);
                        }, null);
                    }
                });
            });
        }

        // --- LOGIKA SESJI ---
        if (plugin.getConfig().getBoolean("features.session-enabled")) {
            if (loginSystem.getSesje().containsKey(uuid)) {

                if (!plugin.getPasswordManager().maHaslo(uuid.toString())) {
                    return;
                }

                if (p.getAddress() == null) return;
                String currentIP = p.getAddress().getAddress().getHostAddress();
                String savedIP = loginSystem.getSesjeIP().get(uuid);

                if (savedIP == null || !savedIP.equals(currentIP)) {
                    loginSystem.getSesje().remove(uuid);
                    loginSystem.getSesjeIP().remove(uuid);
                } else {
                    String timeRaw = plugin.getConfig().getString("features.session-time", "5 minutes").toLowerCase();
                    long sessionLimitMillis = SessionManager.parseSessionTime(timeRaw);
                    long lastLogout = loginSystem.getSesje().get(uuid);

                    if (System.currentTimeMillis() - lastLogout <= sessionLimitMillis) {
                        loginSystem.finishLogin(p);

                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));
                        return; // Sesja przywrócona, kończymy onJoin
                    } else {
                        loginSystem.getSesje().remove(uuid);
                        loginSystem.getSesjeIP().remove(uuid);
                    }
                }
            }
        }

        // --- LOGIKA POZA SESJĄ (STANDARDOWY JOIN) ---
        loginSystem.getStorage().save(p);
        spawnManager.teleport(p, "before_login");
        loginSystem.getZalogowani().remove(uuid);

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
        UUID uuid = e.getUniqueId();
        String currentIP = e.getAddress().getHostAddress();
        IPManager ipManager = loginSystem.getIpManager();

        // 1. JEDYNE SPRAWDZENIE BANA
        if (ipManager.isIPBanned(currentIP)) {
            long totalSeconds = ipManager.getIPBanTimeLeft(currentIP);
            String timeFormatted = formatTime(totalSeconds);
            String reason = ipManager.getBanReason(currentIP); // Pobieramy powód

            String msg;
            if (reason.equals("SPAM")) {
                msg = plugin.getLanguageManager().getMessage("kick-ip-spammed")
                        .replace("%time%", timeFormatted);
            } else {
                // Domyślnie traktujemy to jako ban za hasła (PASSWORD)
                msg = plugin.getLanguageManager().getMessage("kick-max-attempts-ban")
                        .replace("%time%", timeFormatted);
            }

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
            return;
        }

        // 2. OCHRONA PRZED SPAMEM WEJŚĆ (Tylko jeśli nie ma jeszcze bana)
        if (plugin.getConfig().getBoolean("security.ip-security.entry-protection.enabled", true)) {
            ipManager.addIPAttempt(currentIP);
        }

        if (plugin.getConfig().getBoolean("security.ip-security.ip-lock-enabled", true)) {
            String savedIP = ipManager.getIP(uuid.toString());
            if (savedIP != null && !IPSecurity.isIPSafe(savedIP, currentIP)) {
                String msg = plugin.getLanguageManager().getMessage("ip-lock-kick");
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
                return;
            }
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            String zapisaneIP = ipManager.getIP(uuid.toString());
            if (zapisaneIP == null || !IPSecurity.isIPSafe(zapisaneIP, currentIP)) {
                int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
                int ileKont = ipManager.getIloscKontByIP(currentIP);

                if (ileKont >= limit) {
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            plugin.getLanguageManager().getMessage("anti-multiaccount-kick"));
                }
            }
        }
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}