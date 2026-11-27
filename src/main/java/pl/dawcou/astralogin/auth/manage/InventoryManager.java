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

public class InventoryManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;

    public InventoryManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public void save(Player p) {
        UUID uuid = p.getUniqueId();

        // Jeśli ekwipunek jest już zapisany w cache, nie ruszamy go, żeby nie nadpisać puste/nowe stany
        if (hasSavedInventory(p)) {
            return;
        }

        boolean inventoryEnabled = plugin.getConfig().getBoolean("features.inventory.enabled", true);
        if (!inventoryEnabled) {
            return;
        }

        // Zapis głównego ekwipunku i zbroi w Base64
        String invBase64 = itemStackArrayToBase64(p.getInventory().getContents());
        String armorBase64 = itemStackArrayToBase64(p.getInventory().getArmorContents());

        // Przerywamy zapis, jeśli serializacja się nie powiodła
        if (invBase64 == null || armorBase64 == null) {
            plugin.getLogger().severe("Could not save inventory for player " + p.getName() + " because serialization failed!");
            return;
        }

        playerDataManager.set(uuid, "player.inventory.contents", invBase64);
        playerDataManager.set(uuid, "player.inventory.armor", armorBase64);

        // Zapis trybu gry
        boolean saveGamemode = plugin.getConfig().getBoolean("features.inventory.save-gamemode", true);
        if (saveGamemode) {
            playerDataManager.set(uuid, "player.gamemode", p.getGameMode().name());
        }

        // Czyszczenie ekwipunku
        p.getInventory().clear();
    }

    public void restore(Player p) {
        UUID uuid = p.getUniqueId();

        // Jeśli nie ma nic do przywrócenia, nie ruszamy
        if (!hasSavedInventory(p)) {
            return;
        }

        // Zbieramy przedmioty zdobyte przed zalogowaniem
        List<ItemStack> newItems = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null) {
                newItems.add(item.clone());
            }
        }


        // Odczytywanie zapisanych danych ekwipunku
        String invBase64 = playerDataManager.getString(uuid, "player.inventory.contents");
        String armorBase64 = playerDataManager.getString(uuid, "player.inventory.armor");

        // Deserializacja przed rozpoczęciem przywracania
        ItemStack[] inv = null;
        ItemStack[] armor = null;

        if (invBase64 != null) {
            inv = itemStackArrayFromBase64(invBase64);
        }

        if (armorBase64 != null) {
            armor = itemStackArrayFromBase64(armorBase64);
        }

        // Dopiero po poprawnej deserializacji obu części przywracamy ekwipunek
        if (inv != null) {
            p.getInventory().setContents(inv);
        }

        if (armor != null) {
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
        if (playerDataManager.has(uuid, "player.gamemode")) {
            String gmName = playerDataManager.getString(uuid, "player.gamemode");
            try {
                GameMode gm = GameMode.valueOf(gmName != null ? gmName : "SURVIVAL");
                p.setGameMode(gm);
            } catch (Exception e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        if (inv != null && armor != null) {
            deleteInventoryCache(uuid);
        }
    }

    private boolean hasSavedInventory(Player p) {
        return playerDataManager.has(p.getUniqueId(), "player.inventory")
                || playerDataManager.has(p.getUniqueId(), "player.gamemode");
    }

    public void deleteInventoryCache(UUID uuid) {
        playerDataManager.remove(uuid, "player.inventory");
        playerDataManager.remove(uuid, "player.gamemode");
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

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Could not serialize items to Base64: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int length = dataInput.readInt();

            if (length < 0 || length > 256) {
                throw new IllegalArgumentException("Invalid inventory array length: " + length);
            }

            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not deserialize items from Base64: " + e.getMessage());
            return null;
        }
    }
}