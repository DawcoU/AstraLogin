package pl.dawcou.astralogin.listeners.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.LoginSystem;
import pl.dawcou.astralogin.auth.manage.spawn.SpawnType;
import pl.dawcou.astralogin.auth.security.ip.IPTrustManager;

import java.time.Duration;
import java.util.UUID;

public class JoinHandler {

    private final AstraLogin plugin;
    private final JoinSessionHandler sessionHandler;
    private final JoinTimerHandler timerHandler;

    public JoinHandler(AstraLogin plugin) {
        this.plugin = plugin;
        this.sessionHandler = new JoinSessionHandler(plugin);
        this.timerHandler = new JoinTimerHandler(plugin);
    }

    public void handleJoin(PlayerJoinEvent e) {
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

            plugin.getSchedulerManager().runForEntity(p, () -> {
                IPTrustManager.TrustLevel level = plugin.getIpTrustManager().getTrustLevel(ip);
                if (premium && (level != IPTrustManager.TrustLevel.FATAL && !plugin.getPlayerDataManager().getBoolean(uuid, "account.suspicious", false))) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("premium.success"));
                    plugin.getLogManager().log("Premium account detected for player " + p.getName());

                    loginSystem.finishSession(p);
                    return;
                }

                // Obsługa sesji wyekstrahowana do osobnej klasy
                boolean passwordBypassedBySession = sessionHandler.handleSession(p, uuid, level, loginSystem);

                // Jeśli sesja przywróciła gracza całkowicie (hasło + 2FA lub samo hasło bez 2FA), przerwij
                if (plugin.getLoginSystem().getLoggedIn().contains(uuid) && !plugin.getLoginSystem().isWaitingFor2FA(uuid)) {
                    return;
                }

                // --- LOGIKA POZA SESJĄ LUB DLA BLOKADY 2FA ---
                if (plugin.getConfig().getBoolean("visuals.hide-unlogged-players")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        plugin.getSchedulerManager().runForEntity(online, () -> online.hidePlayer(plugin, p));
                    }
                }

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

                // Timer logowania / wpisania kodu 2FA wyekstrahowany do osobnej klasy
                timerHandler.startLoginTimer(p, uuid, loginSystem);
            });
        });
    }
}