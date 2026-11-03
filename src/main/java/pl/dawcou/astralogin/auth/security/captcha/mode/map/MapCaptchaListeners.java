package pl.dawcou.astralogin.auth.security.captcha.mode.map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerFailedCaptchaEvent;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.Objects;

public class MapCaptchaListeners implements Listener {

    private final AstraLogin plugin;

    public MapCaptchaListeners(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!MapCaptchaHandler.hasActiveCaptcha(player)) {
            return;
        }

        // Anulujemy wiadomość na czacie, żeby kod nie wyświetlił się
        event.setCancelled(true);

        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();
        String message = event.getMessage().replace(" ", "");

        plugin.getSchedulerManager().runForEntity(player, () -> {
            if (MapCaptchaHandler.verifyCode(player, message)) {
                // Zdejmujemy mapę z ręki
                MapCaptchaHandler.removeSession(player, plugin);

                plugin.getAttemptManager().clearCaptchaAttempts(player.getUniqueId());
                plugin.getCaptchaManager().handleCaptchaSuccess(player);

                plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix(player, "captcha.success"));
            } else {
                PlayerFailedCaptchaEvent captchaEvent = new PlayerFailedCaptchaEvent(player, ip);
                Bukkit.getPluginManager().callEvent(captchaEvent);
                if (captchaEvent.isCancelled()) {
                    return;
                }

                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);

                // Przekazujemy błąd do AttemptManager (ten sam menedżer co w GUI)
                plugin.getAttemptManager().addCaptchaTrial(player, AttemptType.CAPTCHA_MAP);
            }
        });
    }
}