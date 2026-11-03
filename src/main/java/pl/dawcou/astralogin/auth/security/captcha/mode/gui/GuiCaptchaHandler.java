package pl.dawcou.astralogin.auth.security.captcha.mode.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.captcha.generator.CaptchaChallengeGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiCaptchaHandler {

    private static final Map<UUID, Set<Material>> activeChallenges = new ConcurrentHashMap<>();
    // Mapa przechowywująca wygenerowane przedmioty dla aktywnej sesji
    private static final Map<UUID, List<ItemStack>> activeItems = new ConcurrentHashMap<>();

    public static void openCaptchaGui(Player player, AstraLogin plugin) {
        int rows = plugin.getConfig().getInt("security.captcha.gui.rows", 6);
        int size = Math.min(Math.max(rows, 1), 6) * 9;

        List<ItemStack> items;
        String langKey;

        if (activeChallenges.containsKey(player.getUniqueId()) && activeItems.containsKey(player.getUniqueId())) {
            items = activeItems.get(player.getUniqueId());
            if (player.hasMetadata("astralogin_captcha_lang")) {
                langKey = (String) player.getMetadata("astralogin_captcha_lang").get(0).value();
            } else {
                langKey = "captcha.gui.prompts.edible";
            }
        } else {
            CaptchaChallengeGenerator.ChallengeResult challenge = CaptchaChallengeGenerator.generate(size, plugin);
            activeChallenges.put(player.getUniqueId(), challenge.getTargetMaterials());
            items = challenge.getItems(size);
            activeItems.put(player.getUniqueId(), items);
            langKey = challenge.getLangKey();

            player.setMetadata("astralogin_captcha_lang", new org.bukkit.metadata.FixedMetadataValue(plugin, langKey));
        }

        String baseTitle = plugin.getLanguageManager().getMessage("captcha.gui.title");
        String promptText = plugin.getLanguageManager().getMessage(langKey);
        String fullTitle = baseTitle + " " + promptText;

        Inventory gui = Bukkit.createInventory(null, size, fullTitle);

        for (int i = 0; i < items.size(); i++) {
            gui.setItem(i, items.get(i));
        }

        player.openInventory(gui);
    }

    public static boolean verifyClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !activeChallenges.containsKey(player.getUniqueId())) {
            return false;
        }
        Set<Material> expectedMaterials = activeChallenges.get(player.getUniqueId());
        return expectedMaterials.contains(clickedItem.getType());
    }

    public static void removeSession(Player player) {
        activeChallenges.remove(player.getUniqueId());
        activeItems.remove(player.getUniqueId());
        player.removeMetadata("astralogin_captcha_lang", AstraLogin.getInstance());
    }

    public static boolean hasActiveCaptcha(Player player) {
        return activeChallenges.containsKey(player.getUniqueId());
    }
}