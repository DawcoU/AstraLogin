package pl.dawcou.astralogin.auth.security.captcha;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerCaptchaStartEvent;
import pl.dawcou.astralogin.api.events.PlayerCaptchaSuccessEvent;
import pl.dawcou.astralogin.auth.security.captcha.mode.chat.ChatCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.gui.GuiCaptchaHandler;
import pl.dawcou.astralogin.auth.security.captcha.mode.map.MapCaptchaHandler;
import pl.dawcou.astralogin.auth.security.suspicious.SuspicionLevel;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {

    private final AstraLogin plugin;
    private final Set<UUID> flaggedPlayers = ConcurrentHashMap.newKeySet();

    public CaptchaManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void flagForCaptcha(UUID uuid) {
        flaggedPlayers.add(uuid);
    }

    public boolean isFlagged(UUID uuid) {
        return flaggedPlayers.contains(uuid);
    }

    /**
     * Sprawdza, czy gracz potrzebuje captchy (z uwzględnieniem flagi, wymuszenia oraz automatycznego interwału).
     */
    public boolean needsCaptcha(UUID uuid) {
        if (!plugin.getConfig().getBoolean("security.captcha.enabled", true)) {
            return false;
        }

        // Jeśli wymuszono, gracz jest na liście flagged LUB ma flagę podejrzanego konta w danych
        if (flaggedPlayers.contains(uuid) || plugin.getSuspicionManager().hasAtLeast(uuid, SuspicionLevel.MEDIUM)) {
            return true;
        }

        // Jeśli automatyczne captchy są wyłączone w configu, sprawdzamy tylko flagi (np. podejrzane IP)
        if (!plugin.getConfig().getBoolean("security.captcha.automatic.enabled", true)) {
            return false;
        }

        // Sprawdzamy timestamp w pliku danych gracza
        long captchaExpiry = plugin.getPlayerDataManager().getLong(uuid, "captcha.timestamp", 0L);
        return System.currentTimeMillis() > captchaExpiry;
    }

    public boolean hasCaptcha(Player p) {
        return MapCaptchaHandler.hasActiveCaptcha(p) ||
                GuiCaptchaHandler.hasActiveCaptcha(p) ||
                ChatCaptchaHandler.hasActiveCaptcha(p);
    }

    public void startCaptcha(Player player) {
        if (!plugin.getConfig().getBoolean("security.captcha.enabled", true)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        if (!needsCaptcha(uuid)) {
            return;
        }

        PlayerCaptchaStartEvent event = new PlayerCaptchaStartEvent(player, ip);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        flaggedPlayers.remove(uuid);

        boolean isRandom = plugin.getConfig().getBoolean("security.captcha.random-mode", false);
        String mode = plugin.getConfig().getString("security.captcha.mode", "GUI");

        if (isRandom) {
            int pick = java.util.concurrent.ThreadLocalRandom.current().nextInt(3);
            mode = switch (pick) {
                case 0 -> "MAP";
                case 1 -> "CHAT";
                default -> "GUI";
            };
        }

        switch (mode.toUpperCase()) {
            case "MAP" -> {
                if (plugin.getBackendManager().isPacketEventsAvailable()) {
                    MapCaptchaHandler.openMapCaptcha(player, plugin);
                } else {
                    GuiCaptchaHandler.openCaptchaGui(player, plugin);
                }
            }
            case "CHAT" -> ChatCaptchaHandler.openChatCaptcha(player, plugin);
            default -> GuiCaptchaHandler.openCaptchaGui(player, plugin);
        }
    }

    /**
     * Wywoływane po pomyślnym ukończeniu captchy – zapisuje nowy timestamp na podstawie interwału z configu.
     */
    public void handleCaptchaSuccess(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        PlayerCaptchaSuccessEvent event = new PlayerCaptchaSuccessEvent(player, ip);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        flaggedPlayers.remove(uuid);
        plugin.getAttemptManager().clearCaptchaAttempts(uuid);

        if (!plugin.getConfig().getBoolean("security.captcha.automatic.enabled", true)) {
            return;
        }

        String intervalConfig = plugin.getConfig().getString("security.captcha.automatic.interval", "5 days");
        long intervalMillis = TimeUtils.parseTime(intervalConfig, 432000L);
        long expiryTimestamp = System.currentTimeMillis() + intervalMillis;

        plugin.getPlayerDataManager().set(uuid, "captcha.timestamp", expiryTimestamp);
        plugin.getSuspicionManager().decrementSuspicion(player.getUniqueId());

        plugin.getIpTrustManager().changeTrustScore(
                ip,
                plugin.getIpTrustManager().getCaptchaSuccessPoints()
        );

        // Wznowienie logowania
        plugin.getLoginListeners().continueLoginFlow(player, uuid, ip);
    }
}