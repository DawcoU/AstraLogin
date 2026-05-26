package pl.dawcou.astralogin;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;

public class InventoryManager {

    private final File file;
    private FileConfiguration config;
    private final AstraLogin plugin;

    public InventoryManager(AstraLogin plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.file = new File(dataFolder, "inventory_data.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save(Player p) {
        String uuid = p.getUniqueId().toString();

        // KROK 1: Absolutne bezpieczeństwo danych.
        // Jeśli plik zawiera już UUID gracza (bo np. wyszedł niezalogowany), to NIEZALEŻNIE
        // od tego, czy admin właśnie wyłączył opcję w configu, musimy wyczyścić mu tymczasowe EQ
        // i ustawić survival, ponieważ jego prawdziwe przedmioty już bezpiecznie leżą w pliku!
        if (config.contains(uuid)) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.setGameMode(GameMode.SURVIVAL);
            return;
        }

        // KROK 2: Sprawdzenie głównego configu
        boolean inventoryEnabled = plugin.getConfig().getBoolean("features.inventory.enabled", true);
        if (!inventoryEnabled) {
            return; // Opcja jest wyłączona, a gracz nie miał zapisu? Wychodzimy! Nie dotykamy jego EQ ani GM.
        }

        // Zapisujemy KAŻDY slot z osobna, żeby Bukkit się nie pogubił
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) config.set(uuid + ".inv." + i, inv[i]);
        }

        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) config.set(uuid + ".arm." + i, armor[i]);
        }

        // KROK 3: Opcjonalne zapisywanie GameMode
        boolean saveGamemode = plugin.getConfig().getBoolean("features.inventory.save-gamemode", true);
        if (saveGamemode) {
            config.set(uuid + ".gamemode", p.getGameMode().name());
        }

        save(); // Zapisujemy plik cache (inventory_cache.yml)

        // Czyszczenie po pomyślnym zapisie
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        if (saveGamemode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void restore(Player p) {
        String uuid = p.getUniqueId().toString();

        // KLUCZOWE: Sprawdzamy tylko, czy gracz MA dane w pliku cache.
        // Nie interesuje nas tutaj config. Jeśli dane tam są, to znaczy, że musimy je oddać!
        if (!config.contains(uuid)) return;

        // Przywracamy EQ (36 slotów + dodatkowe)
        ItemStack[] inv = new ItemStack[p.getInventory().getSize()];
        if (config.getConfigurationSection(uuid + ".inv") != null) {
            for (String key : config.getConfigurationSection(uuid + ".inv").getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = config.getItemStack(uuid + ".inv." + key);

                if (item != null) {
                    inv[slot] = item;
                }
            }
        }
        p.getInventory().setContents(inv);

        // Przywracamy Armor
        ItemStack[] armor = new ItemStack[4];
        if (config.getConfigurationSection(uuid + ".arm") != null) {
            for (String key : config.getConfigurationSection(uuid + ".arm").getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = config.getItemStack(uuid + ".arm." + key);

                if (item != null) {
                    armor[slot] = item;
                }
            }
        }
        p.getInventory().setArmorContents(armor);

        // KROK 4: Przywracamy GameMode Inteligentnie
        // Zamiast ślepego sprawdzania configu, patrzymy czy klucz ".gamemode" w ogóle istnieje w pliku gracza.
        // Dzięki temu, jeśli funkcja była wyłączona podczas zapisu, nie nadpiszemy graczowi jego trybu.
        if (config.contains(uuid + ".gamemode")) {
            String gmName = config.getString(uuid + ".gamemode", "SURVIVAL");
            try {
                GameMode gm = GameMode.valueOf(gmName);
                p.setGameMode(gm);
            } catch (Exception e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Czyszczenie danych po przywróceniu
        config.set(uuid, null);
        save();
    }

    private void save() {
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}