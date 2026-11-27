
package pl.dawcou.astralogin.auth.security.captcha.mode.map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.captcha.generator.MapCaptchaGenerator;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MapCaptchaHandler {

    private static final Map<UUID, String> activeMapCodes = new ConcurrentHashMap<>();
    private static final SecureRandom secureRandom = new SecureRandom();

    public static void openMapCaptcha(Player player, AstraLogin plugin) {
        if (!plugin.getBackendManager().isPacketEventsAvailable()) {
            return;
        }

        MapCaptchaGenerator.ChallengeResult challenge = MapCaptchaGenerator.generate(plugin);
        activeMapCodes.put(player.getUniqueId(), challenge.code());

        int mapId = 1_000_000_000 + secureRandom.nextInt(1_000_000_000);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();

        if (meta != null) {
            meta.setMapId(mapId);
            meta.setScaling(false);
            meta.setDisplayName(plugin.getLanguageManager().getMessage("captcha.modes.map.item-name"));
            mapItem.setItemMeta(meta);
        }

        player.getInventory().setItemInMainHand(mapItem);

        // PacketEvents class is created only when PacketEvents is available.
        PacketEventsMapSender sender = new PacketEventsMapSender();
        sender.sendMap(player, mapId, challenge);

        player.sendMessage(plugin.getLanguageManager().getWithPrefix(
                player,
                "captcha.modes.map.instruction"
        ));
    }

    public static boolean verifyCode(Player player, String inputCode) {
        UUID uuid = player.getUniqueId();

        if (!activeMapCodes.containsKey(uuid)) {
            return false;
        }

        String correctCode = activeMapCodes.get(uuid);
        return correctCode.equalsIgnoreCase(inputCode.trim());
    }

    public static void removeSession(Player player, AstraLogin plugin) {
        activeMapCodes.remove(player.getUniqueId());

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (mainHand.getType() == Material.FILLED_MAP
                && mainHand.hasItemMeta()
                && mainHand.getItemMeta() instanceof MapMeta meta) {

            String expectedName = plugin.getLanguageManager()
                    .getMessage("captcha.modes.map.item-name");

            if (meta.hasDisplayName() && meta.getDisplayName().equals(expectedName)) {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    public static boolean hasActiveCaptcha(Player player) {
        return activeMapCodes.containsKey(player.getUniqueId());
    }
}
