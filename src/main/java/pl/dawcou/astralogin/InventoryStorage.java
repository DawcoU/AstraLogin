package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;

public class InventoryStorage {

    private final File file;
    private final FileConfiguration cfg;

    public InventoryStorage(JavaPlugin plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.file = new File(dataFolder, "inventory_storage.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void hide(Player p) {
        String uuid = p.getUniqueId().toString();
        if (cfg.contains(uuid)) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            return;
        }

        // Zapisujemy KAŻDY slot z osobna, żeby Bukkit się nie pogubił
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) cfg.set(uuid + ".inv." + i, inv[i]);
        }

        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) cfg.set(uuid + ".arm." + i, armor[i]);
        }

        save();
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
    }

    public void restore(Player p) {
        String uuid = p.getUniqueId().toString();
        if (!cfg.contains(uuid)) return;

        // Przywracamy EQ (36 slotów + dodatkowe)
        ItemStack[] inv = new ItemStack[p.getInventory().getSize()];
        if (cfg.getConfigurationSection(uuid + ".inv") != null) {
            for (String key : cfg.getConfigurationSection(uuid + ".inv").getKeys(false)) {
                int slot = Integer.parseInt(key);
                inv[slot] = cfg.getItemStack(uuid + ".inv." + key);
            }
        }
        p.getInventory().setContents(inv);

        // Przywracamy Armor
        ItemStack[] armor = new ItemStack[4];
        if (cfg.getConfigurationSection(uuid + ".arm") != null) {
            for (String key : cfg.getConfigurationSection(uuid + ".arm").getKeys(false)) {
                int slot = Integer.parseInt(key);
                armor[slot] = cfg.getItemStack(uuid + ".arm." + key);
            }
        }
        p.getInventory().setArmorContents(armor);

        cfg.set(uuid, null);
        save();
    }

    private void save() {
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}