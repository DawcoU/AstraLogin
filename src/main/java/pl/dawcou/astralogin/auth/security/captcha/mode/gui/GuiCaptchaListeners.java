package pl.dawcou.astralogin.auth.security.captcha.mode.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.api.events.PlayerFailedCaptchaEvent;
import pl.dawcou.astralogin.auth.security.attempt.AttemptType;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.Objects;

public class GuiCaptchaListeners implements Listener {

    private final AstraLogin plugin;

    public GuiCaptchaListeners(AstraLogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!GuiCaptchaHandler.hasActiveCaptcha(player)) {
            return;
        }

        String rawTitle = plugin.getLanguageManager().getMessage("captcha.gui.title");

        if (event.getView().getTitle().startsWith(rawTitle)) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) {
                return;
            }

            String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

            if (GuiCaptchaHandler.verifyClick(player, event.getCurrentItem())) {
                GuiCaptchaHandler.removeSession(player);
                plugin.getAttemptManager().clearCaptchaAttempts(player.getUniqueId());
                plugin.getCaptchaManager().handleCaptchaSuccess(player);
                player.closeInventory();

                plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix(player, "captcha.success"));
            } else {
                PlayerFailedCaptchaEvent captchaEvent = new PlayerFailedCaptchaEvent(player, ip);
                Bukkit.getPluginManager().callEvent(captchaEvent);
                if (captchaEvent.isCancelled()) {
                    return;
                }

                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);

                // Cała logika zliczania, wiadomości oraz wykopania gracza dzieje się w AttemptManager
                plugin.getAttemptManager().addCaptchaTrial(player, AttemptType.CAPTCHA_GUI);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Sprawdzamy czy gracz ma wciąż aktywną sesję captcha
        if (GuiCaptchaHandler.hasActiveCaptcha(player)) {
            plugin.getSchedulerManager().runSyncLater(() -> {
                if (player.isOnline() && GuiCaptchaHandler.hasActiveCaptcha(player)) {
                    GuiCaptchaHandler.openCaptchaGui(player, plugin);
                }
            }, 1L);
        }
    }
}