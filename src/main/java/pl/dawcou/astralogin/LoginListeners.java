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
    private final SpawnManager spawnManager;

    public LoginListeners(AstraLogin plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.handleQuit(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        LoginSystem ls = plugin.getLoginSystem();
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
            if (plugin.getSessionManager().getSesje().containsKey(uuid)) {

                if (p.getAddress() == null) return;
                String currentIP = p.getAddress().getAddress().getHostAddress();
                String savedIP = plugin.getSessionManager().getSesjeIP().get(uuid);

                // POBIERAMY IP Z GLÓWNEGO IPManagera (Baza danych gracza)
                String mainSavedIP = plugin.getIPManager().getIP(uuid.toString());

                // 1. WERYFIKACJA ADRESU IP (DODANY WARUNEK: mainSavedIP == null)
                if (mainSavedIP == null || savedIP == null || !savedIP.equals(currentIP)) {
                    // Jeśli IP w bazie zostało zresetowane (jest null), albo IP z sesji się nie zgadza:
                    // Bezwzględnie kasujemy całą sesję z RAM-u i pliku i NIE ROBIMY RETURN!
                    plugin.getSessionManager().deleteSession(uuid);

                } else {
                    // 2. WERYFIKACJA CZASU TRWANIA SESJI
                    // POPRAWKA: Wykorzystujemy istniejącą metodę z SessionManager (zmień getSessionLimitMillis() w SessionManager na public!)
                    long sessionLimitMillis = plugin.getSessionManager().getSessionLimitMillis();
                    long lastLogout = plugin.getSessionManager().getSesje().get(uuid);

                    if (System.currentTimeMillis() - lastLogout <= sessionLimitMillis) {
                        // Sesja prawidłowa -> logujemy gracza automatycznie
                        ls.finishLogin(p);

                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));
                        plugin.getLogManager().log("Player " + p.getName() + " had an active session");

                        return; // Przerywamy onJoin, gracz jest pomyślnie zalogowany!
                    } else {
                        // Sesja wygasła czasowo -> czyszczenie całościowe
                        plugin.getSessionManager().deleteSession(uuid);
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
        ls.getStorage().save(p);
        spawnManager.teleport(p, "before_login");
        ls.getZalogowani().remove(uuid);

        if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (ls.getData().hasPassword(uuid.toString())) {
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
                    if (!p.isOnline() || ls.getZalogowani().contains(p.getUniqueId())) {
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
        LoginSystem ls = plugin.getLoginSystem();
        UUID uuid = e.getUniqueId();
        String currentIP = e.getAddress().getHostAddress();
        IPManager ipManager = ls.getIpManager();
        String playerName = e.getName();

        // 1. JEDYNE SPRAWDZENIE BANA
        if (ipManager.isIPBanned(currentIP)) {
            long totalSeconds = ipManager.getIPBanTimeLeft(currentIP);

            // Teraz używamy klasy narzędziowej:
            String timeFormatted = LoginUtils.formatTime(totalSeconds);
            String reason = ipManager.getBanReason(currentIP);
            if (reason == null) reason = "UNKNOWN";

            String msg;
            if ("SPAM".equals(reason)) {
                msg = plugin.getLanguageManager().getMessage("kick-ip-spammed")
                        .replace("%time%", timeFormatted);
            } else {
                // Domyślnie traktujemy to jako ban za hasła (PASSWORD)
                msg = plugin.getLanguageManager().getMessage("kick-max-attempts-ban")
                        .replace("%time%", timeFormatted);
            }

            // Ktoś z banem na IP próbuje się wbić
            plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") tried to connect but is IP banned. Reason: " + reason);

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, net.kyori.adventure.text.Component.text(msg));
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

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, net.kyori.adventure.text.Component.text(msg));
                return;
            }
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            String zapisaneIP = ipManager.getIP(uuid.toString());
            // Tutaj tak samo – czysto i bez śmiecenia dodatkowymi parametrami
            if (zapisaneIP == null || !IPSecurity.CheckIP(zapisaneIP, currentIP)) {
                int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
                int ileKont = ipManager.getNumberOfAccountsByIP(currentIP);

                if (ileKont >= limit) {
                    // Przekroczenie limitu kont na jednym IP
                    plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") was blocked by Anti-MultiAccount. Limit: " + limit + ", Current: " + ileKont);

                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, net.kyori.adventure.text.Component.text(plugin.getLanguageManager().getMessage("anti-multiaccount-kick")));
                    return;
                }
            }
        }
    }

    public void handleQuit(Player p) {
        // 1. Pobieramy aktualne instancje bezpośrednio z pluginu, żeby uniknąć Nullowania
        LoginSystem ls = plugin.getLoginSystem();
        SpawnManager sm = plugin.getSpawnManager();
        SessionManager ss = plugin.getSessionManager();

        // 2. Logika zapisu (tylko jeśli gracz był zalogowany)
        if (ls != null && ls.getZalogowani() != null && ls.getZalogowani().contains(p.getUniqueId())) {

            if (sm != null) {
                sm.saveLastLocation(p);
            }

            if (plugin.getConfig().getBoolean("features.session.enabled") && ss != null) {
                long now = System.currentTimeMillis();
                ss.getSesje().put(p.getUniqueId(), now);
                ss.getSesjeIP().put(p.getUniqueId(), p.getAddress().getAddress().getHostAddress());
            }
        }

        // 3. Logika widoczności (silnikowe odświeżenie)
        for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, p);
        }

        // 4. Usuwamy z mapy zalogowanych
        if (ls != null && ls.getZalogowani() != null) {
            ls.getZalogowani().remove(p.getUniqueId());
        }

        // 5. ATOMOWY ZAPIS (sesje zapisane na dysk raz, na końcu)
        if (ss != null) {
            ss.saveSessionsToConfig();
        }
    }
}