package pl.dawcou.astralogin.auth.security.captcha.mode.chat;

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

public class ChatCaptchaListeners implements Listener {

    private final AstraLogin plugin;

    public ChatCaptchaListeners(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!ChatCaptchaHandler.hasActiveCaptcha(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        plugin.getSchedulerManager().runForEntity(player, () -> {
            if (ChatCaptchaHandler.verifyAnswer(player, message)) {
                ChatCaptchaHandler.removeSession(player);
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
                plugin.getAttemptManager().addCaptchaTrial(player, AttemptType.CAPTCHA_CHAT);
            }
        });
    }
}