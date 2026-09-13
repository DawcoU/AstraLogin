package pl.dawcou.astralogin.auth.manage;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InventoryManager {

    private final File file;
    private FileConfiguration config;
    private final AstraLogin plugin;

    public InventoryManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data/players");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        file = new File(dataFolder, "inventory_data.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save(Player p) {
        String uuid = p.getUniqueId().toString();

        // Czyszczenie tymczasowego EQ, gdy zapis dla gracza juz istnieje
        if (config.contains("inventory." + uuid)) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.setGameMode(GameMode.SURVIVAL);
            return;
        }

        boolean inventoryEnabled = plugin.getConfig().getBoolean("features.inventory.enabled", true);
        if (!inventoryEnabled) {
            return;
        }

        // Zapis glównego ekwipunku
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) {
                config.set("inventory." + uuid + ".inv." + i, inv[i]);
            }
        }

        // Zapis zbroi
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                config.set("inventory." + uuid + ".arm." + i, armor[i]);
            }
        }

        // Zapis trybu gry
        boolean saveGamemode = plugin.getConfig().getBoolean("features.inventory.save-gamemode", true);
        if (saveGamemode) {
            config.set("inventory." + uuid + ".gamemode", p.getGameMode().name());
        }

        save();

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        if (saveGamemode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void restore(Player p) {
        String uuid = p.getUniqueId().toString();

        if (!config.contains("inventory." + uuid)) return;

        // Zbieramy przedmioty zdobyte przed zalogowaniem
        List<ItemStack> newItems = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null) {
                newItems.add(item.clone());
            }
        }

        // Przywracanie glównego ekwipunku
        ItemStack[] inv = new ItemStack[p.getInventory().getSize()];
        if (config.getConfigurationSection("inventory." + uuid + ".inv") != null) {
            for (String key : config.getConfigurationSection("inventory." + uuid + ".inv").getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = config.getItemStack("inventory." + uuid + ".inv." + key);
                if (item != null) {
                    inv[slot] = item;
                }
            }
        }
        p.getInventory().setContents(inv);

        // Przywracanie zbroi
        ItemStack[] armor = new ItemStack[4];
        if (config.getConfigurationSection("inventory." + uuid + ".arm") != null) {
            for (String key : config.getConfigurationSection("inventory." + uuid + ".arm").getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = config.getItemStack("inventory." + uuid + ".arm." + key);
                if (item != null) {
                    armor[slot] = item;
                }
            }
        }
        p.getInventory().setArmorContents(armor);

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
        if (config.contains("inventory." + uuid + ".gamemode")) {
            String gmName = config.getString("inventory." + uuid + ".gamemode", "SURVIVAL");
            try {
                GameMode gm = GameMode.valueOf(gmName);
                p.setGameMode(gm);
            } catch (Exception e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        config.set("inventory." + uuid, null);
        save();
    }

    public void deleteInventoryCache(String uuidString) {
        String path = "inventory." + uuidString;
        if (config.contains(path)) {
            config.set(path, null);
            save();
        }
    }

    public void reload() {
        try {
            // Całkowicie porzucamy stary stan z RAM-u i ładujemy plik od nowa
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        synchronized (file) {
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}