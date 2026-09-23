package pl.dawcou.astralogin.listeners;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.sessions.SessionManager;
import pl.dawcou.astralogin.auth.manage.InventoryManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnManager;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnType;
import pl.dawcou.astralogin.auth.security.premium.protocol.PacketListener;
import pl.dawcou.astralogin.auth.security.ip.IPManager;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;
import pl.dawcou.astralogin.auth.security.TwoFactorManager;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LoginListeners implements Listener {

    private final AstraLogin plugin;

    private final PacketListener packetListener;

    public LoginListeners(AstraLogin plugin, PacketListener packetListener) {
        this.plugin = plugin;
        this.packetListener = packetListener;
    }

    // Everything is secure here. Please do not inspect further, especially not the next 47 lines.

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        handleQuit(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        LoginSystem loginSystem = plugin.getLoginSystem();
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String ip = p.getAddress().getAddress().getHostAddress();

        // Update Checker
        if (plugin.getConfig().getBoolean("settings.check-updates", true)
                && p.hasPermission("astralogin.update")) {

            plugin.getUpdateChecker().checkForUpdates(p);

        }

        plugin.getSchedulerManager().runAsync(() -> {
            boolean premium = plugin.getPremiumManager().isPremium(p);

            plugin.getSchedulerManager().runSync(() -> {
                IPTrustManager.TrustLevel level = plugin.getIpTrustManager().getTrustLevel(ip);
                if (premium && (level != IPTrustManager.TrustLevel.FATAL)) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("premium.success"));
                    plugin.getLogManager().log("Premium account detected for player " + p.getName());
                    loginSystem.finishSession(p);
                    return;
                }

                // Flaga pomocnicza informująca, czy gracz pominął hasło dzięki sesji
                boolean passwordBypassedBySession = false;

                // --- LOGIKA SESJI ---
                if (plugin.getConfig().getBoolean("features.session.enabled") && plugin.getPasswordManager().isRegistered(uuid)
                        && level != IPTrustManager.TrustLevel.FATAL
                        && level != IPTrustManager.TrustLevel.BAD) {
                    if (p.getAddress() != null) {
                        String currentIP = p.getAddress().getAddress().getHostAddress();
                        String mainSavedIP = plugin.getIPManager().getIP(uuid.toString());

                        if (mainSavedIP == null) {
                            plugin.getSessionManager().deleteSession(uuid);
                        }
                        else if (plugin.getSessionManager().hasActiveSession(uuid, currentIP)) {

                            // POBIERAMY STATUSY 2FA DLA TEGO KONKRETNEGO GRACZA
                            boolean is2FAEnabled = plugin.getTwoFactorManager().has2FA(uuid);

                            if (is2FAEnabled) {
                                // Gracz MA włączone 2FA na koncie -> sprawdzamy sesję drugiego stopnia
                                boolean is2FASessionEnabled = plugin.getConfig().getBoolean("security.2fa.session.enabled", true);
                                boolean hasActive2FA = plugin.getSessionManager().getTwoFactorSessionManager().hasActive2FASession(uuid);

                                if (!is2FASessionEnabled || !hasActive2FA) {
                                    // [Kombinacja: Hasło przywrócone, ale brakuje kodu 2FA / sesja 2FA wyłączona]
                                    passwordBypassedBySession = true;
                                    loginSystem.addWaitingFor2FA(uuid, uuid.toString());

                                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("twofactor.required"));
                                    Title title2fa = Title.title(
                                            Component.text(plugin.getLanguageManager().getMessage("title.twofactor")), // Główny tytuł
                                            Component.text(plugin.getLanguageManager().getMessage("twofactor.required")), // Podtytuł
                                            Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                                    );
                                    plugin.getAdventure().player(p).showTitle(title2fa);
                                } else {
                                    // [Kombinacja: Pełna sesja - Oba aktywne]
                                    loginSystem.finishSession(p);
                                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.restored"));
                                    plugin.getLogManager().log("Player " + p.getName() + " had an active session (Password + 2FA)");
                                    return; // Gracz gra!
                                }
                            } else {
                                // GRACZ NIE MA WŁĄCZONEGO 2FA -> skoro sesja hasła jest aktywna, logujemy go od razu!
                                loginSystem.finishSession(p);
                                p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.restored"));
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
                plugin.getInventoryManager().save(p);
                plugin.getSpawnManager().teleport(p, SpawnType.BEFORE_LOGIN);

                // Jeśli hasło NIE zostało przywrócone przez sesję, standardowo wyrzucamy z zalogowanych
                if (!passwordBypassedBySession) {
                    loginSystem.getLoggedIn().remove(uuid);
                } else {
                    // Jeśli hasło przeszło z sesji, upewniamy się, że gracz ma status zalogowanego z hasła
                    if (!loginSystem.getLoggedIn().contains(uuid)) {
                        loginSystem.getLoggedIn().add(uuid);
                    }
                }

                if (plugin.getConfig().getBoolean("visuals.use-blindness")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
                }

                // Dostosowanie przypomnienia w zależności od statusu
                if (passwordBypassedBySession) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("session.twofactor-expired"));
                    Title title2fa = Title.title(
                            Component.text(plugin.getLanguageManager().getMessage("title.twofactor")), // Główny tytuł
                            Component.text(plugin.getLanguageManager().getMessage("twofactor.required")), // Podtytuł
                            Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                    );
                    plugin.getAdventure().player(p).showTitle(title2fa);
                } else {
                    if (plugin.getPasswordManager().isRegistered(uuid)) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("login.reminder"));

                        // WYSYŁANIE SAMEGO SUBTYTUŁU DLA LOGOWANIA:
                        Title titleLogin = Title.title(
                                Component.empty(), // Czysty, pusty komponent jako główny tytuł
                                Component.text(plugin.getLanguageManager().getMessage("login.reminder")), // Wiadomość ląduje w podtytule
                                Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                        );
                        plugin.getAdventure().player(p).showTitle(titleLogin);
                    } else {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("register.reminder"));

                        // WYSYŁANIE SAMEGO SUBTYTUŁU DLA REJESTRACJI:
                        Title titleRegister = Title.title(
                                Component.empty(), // Czysty, pusty komponent jako główny tytuł
                                Component.text(plugin.getLanguageManager().getMessage("register.reminder")), // Wiadomość ląduje w podtytule
                                Title.Times.times(Duration.ofMillis(500), Duration.ofHours(1), Duration.ofMillis(500))
                        );
                        plugin.getAdventure().player(p).showTitle(titleRegister);
                    }
                }

                // Timer logowania / wpisania kodu 2FA
                if (plugin.getConfig().getBoolean("features.timer.enabled")) {
                    String rawTime = plugin.getConfig().getString("features.timer.time-limit", "2 minutes");
                    long parsedMillis = TimeUtils.parseTime(rawTime, 60000L);

                    long clampedMillis = Math.max(40000L, Math.min(240000L, parsedMillis));

                    final int maxTime = (int) (clampedMillis / 1000);
                    final int[] time = {maxTime};
                    boolean useBossBar = plugin.getConfig().getBoolean("features.timer.boss-bar.use", true);

                    BossBar.Color color;
                    try {
                        color = BossBar.Color.valueOf(plugin.getConfig().getString("features.timer.boss-bar.color", "RED").toUpperCase());
                    } catch (IllegalArgumentException ex) { color = BossBar.Color.RED; }

                    BossBar.Overlay overlay;
                    try {
                        overlay = BossBar.Overlay.valueOf(plugin.getConfig().getString("features.timer.boss-bar.style", "PROGRESS").toUpperCase());
                    } catch (IllegalArgumentException ex) { overlay = BossBar.Overlay.PROGRESS; }

                    final BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f, color, overlay);

                    if (useBossBar) {
                        plugin.getAdventure().player(p).showBossBar(bossBar);
                    }

                    plugin.getSchedulerManager().runAsyncRepeating(task -> {
                        if (!p.isOnline() || (loginSystem.getLoggedIn().contains(p.getUniqueId()) && !loginSystem.isWaitingFor2FA(p.getUniqueId()))) {
                            if (useBossBar) {
                                plugin.getAdventure().player(p).hideBossBar(bossBar);
                            }
                            task.cancel();
                            return;
                        }

                        if (time[0] <= 0) {
                            task.cancel();

                            plugin.getSchedulerManager().runSync(() -> {
                                if (useBossBar) {
                                    plugin.getAdventure().player(p).hideBossBar(bossBar);
                                }

                                String timeoutReason = plugin.getLanguageManager().getMessage("security.timeout");
                                p.kickPlayer(timeoutReason);
                            });

                            plugin.getLogManager().log("Player " + p.getName() + " Was kicked for exceeding the login timeout");
                            return;
                        }

                        String rawMsg = plugin.getLanguageManager()
                                .getMessage("session.timer")
                                .replace("%time%", TimeUtils.formatTime(time[0]));

                        Component message = LegacyComponentSerializer.legacySection().deserialize(rawMsg);

                        if (useBossBar) {
                            float progress = (float) time[0] / maxTime;
                            bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                            bossBar.name(message);
                        } else {
                            plugin.getAdventure().player(p).sendActionBar(message);
                        }

                        time[0]--;
                    }, 0, 1, TimeUnit.SECONDS);
                }
            });
        });
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        IPManager ipManager = plugin.getIPManager();

        String currentIP = e.getAddress().getHostAddress();
        String savedIP = ipManager.getIP(uuid.toString());

        String playerName = e.getName();

        // Wykrywanie lokalnego adresu IP
        if (currentIP.equalsIgnoreCase("127.0.0.1") || currentIP.equalsIgnoreCase("::1")) {
            plugin.getLogger().warning("Detected local IP address (" + currentIP + ") for player " + playerName + "!");
            plugin.getLogger().warning("Your server is likely running behind a Proxy (BungeeCord/Velocity) without proper IP forwarding enabled.");
            plugin.getLogger().warning("Do NOT use IP-based security features, as all players will share the same local IP!");
        }

        // OCHRONA PRZED SPAMEM WEJŚĆ (Tylko jeśli nie ma jeszcze bana)
        if (plugin.getConfig().getBoolean("security.anti-spam.enabled", true)) {
            ipManager.addIPAttempt(currentIP);
        }

        // Sprawdzamy, czy ktoś o tym nicku (bez względu na wielkość liter) gra już teraz na serwerze
        boolean isAlreadyOnline = Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));

        if (isAlreadyOnline) {
            String kickReason = plugin.getLanguageManager().getMessage("login.already-online");

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickReason);
            plugin.getLogManager().log("Someone tried to join the account of an active player (" + playerName + ") with IP (" + currentIP + ")");
            plugin.getIpTrustManager().addTrustScore(
                    currentIP,
                    plugin.getIpTrustManager().getActiveAccountAttemptPoints()
            );
            return;
        }

        String registeredName = plugin.getAccountManager().getRegisteredNameIgnoreCase(playerName);

        // Jeśli znaleziono nick w pliku kont
        if (registeredName != null) {

            // Porównanie wielkości liter
            if (!registeredName.equals(playerName)) {
                String kickReason = plugin.getLanguageManager().getMessage("login.wrong-casing")
                        .replace("%registered%", registeredName);

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickReason);
                plugin.getLogManager().log("Player " + playerName + " tried to join with wrong casing. Registered name: " + registeredName);
                return;
            }
        }

        // JEDYNE SPRAWDZENIE BANA
        if (ipManager.isIPBanned(currentIP)) {
            long totalSeconds = ipManager.getIPBanTimeLeft(currentIP);

            // Teraz używamy klasy narzędziowej:
            String timeFormatted = TimeUtils.formatTime(totalSeconds);
            String reason = ipManager.getBanReason(currentIP);
            if (reason == null) reason = "UNKNOWN";

            String msg;
            if ("SPAM".equals(reason)) {
                msg = plugin.getLanguageManager().getMessage("security.ip-spam")
                        .replace("%time%", timeFormatted);
            } else {
                // Domyślnie traktujemy to jako ban za hasła (PASSWORD)
                msg = plugin.getLanguageManager().getMessage("security.max-attempts-ban")
                        .replace("%time%", timeFormatted);
            }

            // Ktoś z banem na IP próbuje wbić
            plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") tried to connect but is IP banned. Reason: " + reason);

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (msg));
            return;
        }

        if (plugin.getConfig().getBoolean("security.ip-security.enabled", true) && savedIP != null && !plugin.getIPManager().checkIP(uuid.toString(), savedIP, currentIP)) {
            String msg = plugin.getLanguageManager().getMessage("security.ip-mismatch");

            // Ktoś zna hasło/wchodzi na konto, ale IP się nie zgadza z zapisanym
            plugin.getLogManager().log("Player " + playerName + " was blocked by IP-Lock. Current IP: " + currentIP + ", Saved IP: " + savedIP);
            plugin.getIpTrustManager().addTrustScore(
                    currentIP,
                    plugin.getIpTrustManager().getUnknownIpLoginPoints()
            );

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (msg));
            return;
        }

        if (plugin.getConfig().getBoolean("security.anti-multiaccount.enabled", true)) {
            if (savedIP == null) {
                int limit = plugin.getConfig().getInt("security.anti-multiaccount.limit", 2);
                int accountCount = ipManager.getNumberOfAccountsByIP(currentIP);

                if (accountCount >= limit) {
                    // Przekroczenie limitu kont na jednym IP
                    plugin.getLogManager().log("Player " + playerName + " (" + currentIP + ") was blocked by Anti-MultiAccount. Limit: " + limit + ", Current: " + accountCount);
                    plugin.getIpTrustManager().addTrustScore(
                            currentIP,
                            plugin.getIpTrustManager().getMultiIpPoints()
                    );

                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (plugin.getLanguageManager().getMessage("security.multiaccount")));
                    return;
                }
            }
        }
    }

    public void handleQuit(Player p, boolean isShutdown) {
        // Pobieramy aktualne instancje bezpośrednio z pluginu
        UUID uuid = p.getUniqueId();
        LoginSystem loginSystem = plugin.getLoginSystem();
        SpawnManager spawnManager = plugin.getSpawnManager();
        SessionManager sessionManager = plugin.getSessionManager();
        InventoryManager inventoryManager = plugin.getInventoryManager();
        TwoFactorManager twoFactorManager = plugin.getTwoFactorManager();

        if (loginSystem != null && loginSystem.getLoggedIn() != null) {

            if (loginSystem.getLoggedIn().contains(p.getUniqueId())) {

                // --- GRACZ BYŁ ZALOGOWANY ---
                if (spawnManager != null) {
                    spawnManager.saveLastLocation(p);
                }

                if (plugin.getConfig().getBoolean("features.session.enabled") && sessionManager != null) {
                    sessionManager.saveSession(p.getUniqueId(), p.getAddress().getAddress().getHostAddress());
                }

            } else {
                // --- GRACZ NIE BYŁ ZALOGOWANY ---
                if (inventoryManager != null) {
                    inventoryManager.restore(p);
                }
            }
        }

        // Logika widoczności (silnikowe odświeżenie)
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, p);
        }

        // Usuwamy z mapy zalogowanych
        if (loginSystem != null && loginSystem.getLoggedIn() != null) {
            loginSystem.getLoggedIn().remove(p.getUniqueId());
        }

        // ZAPIS (sesji)
        if (sessionManager != null) {
            sessionManager.reload();
            sessionManager.getTwoFactorSessionManager().reload();
        }

        // CZYSZCZENIE (zamrożonych setup'ów)
        if (twoFactorManager != null) {
            twoFactorManager.invalidateSetup(uuid);
        }

        // Czyszczenie pamięci
        plugin.getPremiumManager().removeAuthenticatedPlayer(uuid);
        plugin.getPremiumManager().clearPendingBypass(uuid);

        plugin.getPasswordManager().getPasswordHasher().cleanupPlayer(uuid);

        if (!isShutdown) {
            plugin.getPlayerDataManager().unloadPlayer(uuid);
        }
    }
}