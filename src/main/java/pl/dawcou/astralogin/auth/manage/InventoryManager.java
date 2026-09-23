package pl.dawcou.astralogin.auth.manage;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.util.Base64;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//--------------------------------------------------
// Menedżer ekwipunku graczy oparty na PlayerDataManager
//--------------------------------------------------
public class InventoryManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;

    public InventoryManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public void save(Player p) {
        UUID uuid = p.getUniqueId();

        // Czyszczenie tymczasowego EQ, gdy zapis dla gracza juz istnieje
        if (playerDataManager.has(uuid, "inventory.contents")) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.setGameMode(GameMode.SURVIVAL);
            return;
        }

        boolean inventoryEnabled = plugin.getConfig().getBoolean("features.inventory.enabled", true);
        if (!inventoryEnabled) {
            return;
        }

        // Zapis glównego ekwipunku i zbroi w Base64
        String invBase64 = itemStackArrayToBase64(p.getInventory().getContents());
        String armorBase64 = itemStackArrayToBase64(p.getInventory().getArmorContents());

        playerDataManager.set(uuid, "inventory.contents", invBase64);
        playerDataManager.set(uuid, "inventory.armor", armorBase64);

        // Zapis trybu gry
        boolean saveGamemode = plugin.getConfig().getBoolean("features.inventory.save-gamemode", true);
        if (saveGamemode) {
            playerDataManager.set(uuid, "inventory.gamemode", p.getGameMode().name());
        }

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        if (saveGamemode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void restore(Player p) {
        UUID uuid = p.getUniqueId();

        if (!playerDataManager.has(uuid, "inventory.contents")) return;

        // Zbieramy przedmioty zdobyte przed zalogowaniem
        List<ItemStack> newItems = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null) {
                newItems.add(item.clone());
            }
        }

        // Przywracanie glównego ekwipunku
        String invBase64 = playerDataManager.getString(uuid, "inventory.contents");
        if (invBase64 != null) {
            ItemStack[] inv = itemStackArrayFromBase64(invBase64);

            p.getInventory().setContents(inv);

        }

        // Przywracanie zbroi
        String armorBase64 = playerDataManager.getString(uuid, "inventory.armor");
        if (armorBase64 != null) {
            ItemStack[] armor = itemStackArrayFromBase64(armorBase64);

            p.getInventory().setArmorContents(armor);
        }

        // Losowe przydzielanie nowych przedmiotów do wolnych slotów
        if (!newItems.isEmpty()) {
            Collections.shuffle(newItems);
            for (ItemStack newItem : newItems) {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(newItem);
                if (!leftover.isEmpty()) {
                    // Miejsce w ekwipunku się skończyło
                    break;
                }
            }
        }

        // Przywracanie trybu gry
        if (playerDataManager.has(uuid, "inventory.gamemode")) {
            String gmName = playerDataManager.getString(uuid, "inventory.gamemode");
            try {
                GameMode gm = GameMode.valueOf(gmName != null ? gmName : "SURVIVAL");
                p.setGameMode(gm);
            } catch (Exception e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        deleteInventoryCache(uuid.toString());
    }

    public void deleteInventoryCache(String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            playerDataManager.remove(uuid, "inventory");
        } catch (IllegalArgumentException ignored) {
        }
    }

    //--------------------------------------------------
    // Konwersja ItemStack[] do/z Base64
    //--------------------------------------------------
    private String itemStackArrayToBase64(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            // Zamiast Base64Coder.encodeLines(...)
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Could not serialize items to Base64: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not deserialize items from Base64: " + e.getMessage());
            return new ItemStack[0];
        }
    }
}