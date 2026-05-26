package pl.dawcou.astralogin;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

            if (plugin.getConfig().getBoolean("features.session.enabled")) {
                loginSystem.getSesje().put(uuid, System.currentTimeMillis());
                loginSystem.getSesjeIP().put(uuid, p.getAddress().getAddress().getHostAddress());
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            // "Odkrywamy" go dla wszystkich, żeby silnik wyczyścił stan ukrycia
            online.showPlayer(plugin, p);
        }

        loginSystem.getZalogowani().remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Update Checker
        if (plugin.getConfig().getBoolean("settings.check-updates", true) && p.hasPermission("astralogin.update")) {
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
        if (plugin.getConfig().getBoolean("features.session.enabled")) {
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
                    String timeRaw = plugin.getConfig().getString("features.session.session-time", "5 minutes").toLowerCase();
                    long sessionLimitMillis = SessionManager.parseSessionTime(timeRaw);
                    long lastLogout = loginSystem.getSesje().get(uuid);

                    if (System.currentTimeMillis() - lastLogout <= sessionLimitMillis) {
                        loginSystem.finishLogin(p);

                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));
                        plugin.getLogManager().log("Player " + p.getName() + " had an active session");

                        return; // Sesja przywrócona, kończymy onJoin
                    } else {
                        loginSystem.getSesje().remove(uuid);
                        loginSystem.getSesjeIP().remove(uuid);
                    }
                }
            }
        }

        if (plugin.getConfig().getBoolean("visuals.hide-unlogged-players")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.hidePlayer(plugin, p);
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
        if (plugin.getConfig().getBoolean("features.timer.login-time-enabled")) {
            final int maxTime = plugin.getConfig().getInt("features.timer.login-time-limit");
            final int[] time = {maxTime};

            // Pobieranie ustawień z nowej ścieżki features.timer
            boolean useBossBar = plugin.getConfig().getBoolean("features.timer.use-bossbar", true);

            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(plugin.getConfig().getString("features.timer.bossbar-color", "RED").toUpperCase());
            } catch (IllegalArgumentException ex) {
                color = BossBar.Color.RED;
            }

            BossBar.Overlay overlay;
            try {
                overlay = BossBar.Overlay.valueOf(plugin.getConfig().getString("features.timer.bossbar-style", "PROGRESS").toUpperCase());
            } catch (IllegalArgumentException ex) {
                overlay = BossBar.Overlay.PROGRESS;
            }

            // Inicjalizacja BossBaru (Adventure API)
            final BossBar bossBar = BossBar.bossBar(
                    Component.empty(),
                    1.0f,
                    color,
                    overlay
            );

            if (useBossBar) p.showBossBar(bossBar);

            new BukkitRunnable() {
                @Override
                public void run() {
                    // Sprzątanie
                    if (!p.isOnline() || loginSystem.getZalogowani().contains(p.getUniqueId())) {
                        if (useBossBar) p.hideBossBar(bossBar);
                        this.cancel();
                        return;
                    }

                    // Kick
                    if (time[0] <= 0) {
                        if (useBossBar) p.hideBossBar(bossBar);
                        p.kick(Component.text(plugin.getLanguageManager().getMessage("kick-timeout")));
                        this.cancel();
                        plugin.getLogManager().log("Player " + p.getName() + " Was kicked for exceeding the login timeout");
                        return;
                    }

                    // Przygotowanie wiadomości
                    String rawMsg = plugin.getLanguageManager().getMessage("timer-message")
                            .replace("%time%", String.valueOf(time[0]));
                    Component message = Component.text(rawMsg);

                    // LOGIKA WYBORU: BossBar ALBO ActionBar
                    if (useBossBar) {
                        float progress = (float) time[0] / maxTime;
                        bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                        bossBar.name(message);
                        // Wyświetlamy BossBar (tylko jeśli gracz jeszcze go nie widzi, choć showBossBar jest bezpieczne)
                        p.showBossBar(bossBar);
                    } else {
                        // Jeśli BossBar wyłączony, lejemy info na ActionBar
                        p.sendActionBar(message);
                    }

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
        String playerName = e.getName();

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

            // Ktoś z banem na IP próbuje się wbić
            plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") tried to connect but is IP banned. Reason: " + reason);

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
            return;
        }

        // 2. OCHRONA PRZED SPAMEM WEJŚĆ (Tylko jeśli nie ma jeszcze bana)
        if (plugin.getConfig().getBoolean("security.ip-security.entry-protection.enabled", true)) {
            ipManager.addIPAttempt(currentIP);
        }

        if (plugin.getConfig().getBoolean("security.ip-security.enabled", true)) {
            String savedIP = ipManager.getIP(uuid.toString());
            // Metoda przyjmuje znowu 2 argumenty, bo sama wie ile członów sprawdzać!
            if (savedIP != null && !IPSecurity.CheckIP(savedIP, currentIP)) {
                String msg = plugin.getLanguageManager().getMessage("ip-lock-kick");

                // Ktoś zna hasło/wchodzi na konto, ale IP się nie zgadza z zapisanym
                plugin.getLogManager().log("Player " + playerName + " was blocked by IP-Lock. Current IP: " + currentIP + ", Saved IP: " + savedIP);

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
                return;
            }
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            String zapisaneIP = ipManager.getIP(uuid.toString());
            // Tutaj tak samo – czysto i bez śmiecenia dodatkowymi parametrami
            if (zapisaneIP == null || !IPSecurity.CheckIP(zapisaneIP, currentIP)) {
                int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
                int ileKont = ipManager.getIloscKontByIP(currentIP);

                if (ileKont >= limit) {
                    // Przekroczenie limitu kont na jednym IP
                    plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") was blocked by Anti-MultiAccount. Limit: " + limit + ", Current: " + ileKont);

                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            plugin.getLanguageManager().getMessage("anti-multiaccount-kick"));
                    return;
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