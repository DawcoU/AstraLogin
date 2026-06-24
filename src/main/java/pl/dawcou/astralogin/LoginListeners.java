package pl.dawcou.astralogin;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.UUID;

public class LoginListeners implements Listener {


    private final AstraLogin plugin;
    private final SpawnManager spawnManager;
    private final AccountDataManager manager;

    public LoginListeners(AstraLogin plugin, AccountDataManager manager) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.manager = manager;
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
        String path = "accounts." + p.getUniqueId() + ".";

        // Update Checker
        if (plugin.getConfig().getBoolean("settings.check-updates", true) && p.hasPermission("astralogin.update")) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        p.getScheduler().run(plugin, stask -> {
                            plugin.getNoticeManager().sendUpdateNotice(p, version);
                        }, null);
                    }
                });
            });
        }

        if (ls.getData().hasPassword(uuid.toString())) {
            if (!manager.getConfig().getBoolean(path + "is-registered")) {
                manager.getConfig().set(path + "is-registered", true);
                manager.saveConfig();
            }
        }

        // Flaga pomocnicza informująca, czy gracz pominął hasło dzięki sesji
        boolean passwordBypassedBySession = false;

        // --- LOGIKA SESJI ---
        if (plugin.getConfig().getBoolean("features.session.enabled")) {
            if (p.getAddress() != null) {
                String currentIP = p.getAddress().getAddress().getHostAddress();
                String mainSavedIP = plugin.getIPManager().getIP(uuid.toString());

                if (mainSavedIP == null) {
                    plugin.getSessionManager().deleteSession(uuid);
                }
                else if (plugin.getSessionManager().hasActiveSession(uuid, currentIP)) {

                    // ⚡ POBIERAMY STATUSY 2FA DLA TEGO KONKRETNEGO GRACZA
                    boolean is2FAEnabled = plugin.getAccountDataManager().getConfig().getBoolean("accounts." + uuid + ".2fa-enabled", false);

                    if (is2FAEnabled) {
                        // Gracz MA włączone 2FA na koncie -> sprawdzamy sesję drugiego stopnia
                        boolean is2FASessionEnabled = plugin.getConfig().getBoolean("features.2fa.session.enabled", true);
                        boolean hasActive2FA = plugin.getSessionManager().hasActive2FASession(uuid);

                        if (!is2FASessionEnabled || !hasActive2FA) {
                            // [Kombinacja: Hasło przywrócone, ale brakuje kodu 2FA / sesja 2FA wyłączona]
                            passwordBypassedBySession = true;
                            ls.addWaitingFor2FA(uuid, uuid.toString());

                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("2fa-required"));
                            Title title2fa = Title.title(
                                    Component.text(plugin.getLanguageManager().getMessage("title-2fa")), // Główny tytuł
                                    Component.text(plugin.getLanguageManager().getMessage("2fa-required")), // Podtytuł
                                    Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                            );
                            p.showTitle(title2fa);
                        } else {
                            // [Kombinacja: Pełna sesja - Oba aktywne]
                            ls.finishLogin(p);
                            p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));
                            plugin.getLogManager().log("Player " + p.getName() + " had an active session (Password + 2FA)");
                            return; // Gracz gra!
                        }
                    } else {
                        // GRACZ NIE MA WŁĄCZONEGO 2FA -> skoro sesja hasła jest aktywna, logujemy go od razu!
                        ls.finishLogin(p);
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-restored"));
                        plugin.getLogManager().log("Player " + p.getName() + " had an active session (Password only)");
                        return; // Gracz gra!
                    }
                }
            }
        }

        if (plugin.getConfig().getBoolean("visuals.hide-unlogged-players")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.hidePlayer(plugin, p);
            }
        }

        // --- LOGIKA POZA SESJĄ LUB DLA BLOKADY 2FA ---
        ls.getStorage().save(p);
        spawnManager.teleport(p, "before_login");

        // Jeśli hasło NIE zostało przywrócone przez sesję, standardowo wyrzucamy z zalogowanych
        if (!passwordBypassedBySession) {
            ls.getZalogowani().remove(uuid);
        } else {
            // Jeśli hasło przeszło z sesji, upewniamy się, że gracz ma status zalogowanego z hasła
            if (!ls.getZalogowani().contains(uuid)) {
                ls.getZalogowani().add(uuid);
            }
        }

        if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }

        // Dostosowanie przypomnienia w zależności od statusu
        if (passwordBypassedBySession) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("session-2fa-expired"));
            Title title2fa = Title.title(
                    Component.text(plugin.getLanguageManager().getMessage("title-2fa")), // Główny tytuł
                    Component.text(plugin.getLanguageManager().getMessage("2fa-required")), // Podtytuł
                    Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
            );
            p.showTitle(title2fa);
        } else {
            if (ls.getData().hasPassword(uuid.toString())) {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("reminder-login"));

                // WYSYŁANIE SAMEGO SUBTYTUŁU DLA LOGOWANIA:
                Title titleLogin = Title.title(
                        Component.empty(), // Czysty, pusty komponent jako główny tytuł
                        Component.text(plugin.getLanguageManager().getMessage("reminder-login")), // Wiadomość ląduje w podtytule
                        Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                );
                p.showTitle(titleLogin);
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("reminder-register"));

                // WYSYŁANIE SAMEGO SUBTYTUŁU DLA REJESTRACJI:
                Title titleRegister = Title.title(
                        Component.empty(), // Czysty, pusty komponent jako główny tytuł
                        Component.text(plugin.getLanguageManager().getMessage("reminder-register")), // Wiadomość ląduje w podtytule
                        Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                );
                p.showTitle(titleRegister);
            }
        }

        // Timer logowania / wpisania kodu 2FA
        if (plugin.getConfig().getBoolean("features.timer.login-time-enabled")) {
            // 1. Pobieramy wartość z configu jako String
            String rawTime = plugin.getConfig().getString("features.timer.login-time-limit", "1 minute");
            long parsedMillis = LoginUtils.parseTime(rawTime, 60000L);

            // 3. Nakładamy surowe limity w milisekundach: min 40 000 ms, max 240 000 ms
            long clampedMillis = Math.max(40000L, Math.min(240000L, parsedMillis));

            // 4. Konwertujemy bezpieczny czas z milisekund na sekundy dla reszty timera
            final int maxTime = (int) (clampedMillis / 1000);
            final int[] time = {maxTime};
            boolean useBossBar = plugin.getConfig().getBoolean("features.timer.use-bossbar", true);

            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(plugin.getConfig().getString("features.timer.bossbar-color", "RED").toUpperCase());
            } catch (IllegalArgumentException ex) { color = BossBar.Color.RED; }

            BossBar.Overlay overlay;
            try {
                overlay = BossBar.Overlay.valueOf(plugin.getConfig().getString("features.timer.bossbar-style", "PROGRESS").toUpperCase());
            } catch (IllegalArgumentException ex) { overlay = BossBar.Overlay.PROGRESS; }

            final BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f, color, overlay);
            if (useBossBar) p.showBossBar(bossBar);

            p.getScheduler().runAtFixedRate(plugin, (scheduledTask) -> {
                // Warunek zakończenia: gracz wyszedł LUB (jest zalogowany i NIE oczekuje na 2FA)
                if (!p.isOnline() || (ls.getZalogowani().contains(p.getUniqueId()) && !ls.isWaitingFor2FA(p.getUniqueId()))) {
                    if (useBossBar) p.hideBossBar(bossBar);
                    if (useBossBar) {
                        p.hideBossBar(bossBar);
                    }
                    scheduledTask.cancel();
                    return;
                }

                if (time[0] <= 0) {
                    if (useBossBar) p.hideBossBar(bossBar);
                    p.kick(Component.text(plugin.getLanguageManager().getMessage("kick-timeout")));
                    scheduledTask.cancel();
                    plugin.getLogManager().log("Player " + p.getName() + " Was kicked for exceeding the login timeout");
                    return;
                }

                String rawMsg = plugin.getLanguageManager().getMessage("timer-message").replace("%time%", LoginUtils.formatTime(time[0]));
                Component message = Component.text(rawMsg);

                if (useBossBar) {
                    float progress = (float) time[0] / maxTime;
                    bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                    bossBar.name(message);
                    p.showBossBar(bossBar);
                } else {
                    p.sendActionBar(message);
                }

                time[0]--;
            }, null, 1L, 20L);
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        LoginSystem ls = plugin.getLoginSystem();
        UUID uuid = e.getUniqueId();
        String currentIP = e.getAddress().getHostAddress();
        IPManager ipManager = ls.getIpManager();
        String playerName = e.getName();

        // Sprawdzamy, czy gracz o tym nicku jest już na serwerze
        if (Bukkit.getPlayerExact(playerName) != null) {
            // 1. Pobieramy surowy tekst jako String
            String rawMessage = plugin.getLanguageManager().getMessage("already-online");

            // 2. Zamieniamy String z paragrafami na Component (bez używania MiniMessage)
            Component kickComponent = LegacyComponentSerializer.legacySection().deserialize(rawMessage);

            // 3. Wrzucamy gotowy Component do metody Paper
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickComponent);
            plugin.getLogManager().log("Someone tried to join the account of an active player (" + playerName + ") with IP (" + currentIP + ")");
        }

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
        if (plugin.getConfig().getBoolean("security.anti-spam.enabled", true)) {
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
                ss.saveSession(p.getUniqueId(), p.getAddress().getAddress().getHostAddress());
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

        // 5. ZAPIS (sesje zapisane na dysk raz, na końcu)
        if (ss != null) {
            ss.saveSessionsToConfig();
            ss.save2FAToConfig();
        }
    }
}