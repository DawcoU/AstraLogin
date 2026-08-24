package pl.dawcou.astralogin.auth.manage;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;

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

        // KROK 1: Absolutne bezpieczeństwo danych.
        // Jeśli plik zawiera już UUID gracza (bo np. wyszedł niezalogowany), to NIEZALEŻNIE
        // od tego, czy admin właśnie wyłączył opcję w configu, musimy wyczyścić mu tymczasowe EQ
        // i ustawić survival, ponieważ jego prawdziwe przedmioty już bezpiecznie leżą w pliku!
        if (config.contains("inventory." + uuid)) {
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

        // KROK 1: Zapisujemy KAŻDY slot z osobna z prefiksem 'inventory', żeby Bukkit się nie pogubił
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) {
                config.set("inventory." + uuid + ".inv." + i, inv[i]);
            }
        }

        // KROK 2: Zapisujemy zbroję do nowej sekcji inventory
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                config.set("inventory." + uuid + ".arm." + i, armor[i]);
            }
        }

        // KROK 3: Opcjonalne zapisywanie GameMode w tym samym formacie
        boolean saveGamemode = plugin.getConfig().getBoolean("features.inventory.save-gamemode", true);
        if (saveGamemode) {
            config.set("inventory." + uuid + ".gamemode", p.getGameMode().name());
        }

        save(); // Zapisujemy plik

        // Czyszczenie po pomyślnym zapisie
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        if (saveGamemode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void restore(Player p) {
        String uuid = p.getUniqueId().toString();

        // KLUCZOWE: Sprawdzamy nowy format z prefiksem 'inventory.'
        if (!config.contains("inventory." + uuid)) return;

        // Przywracamy EQ (Wspólniona ścieżka z inventory.)
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

        // Przywracamy Armor (Wspólniona ścieżka z inventory.)
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

        // KROK 4: Przywracamy GameMode Inteligentnie (Wspólniona ścieżka z inventory.)
        if (config.contains("inventory." + uuid + ".gamemode")) {
            String gmName = config.getString("inventory." + uuid + ".gamemode", "SURVIVAL");
            try {
                GameMode gm = GameMode.valueOf(gmName);
                p.setGameMode(gm);
            } catch (Exception e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Czyszczenie danych po przywróceniu z poprawnego klucza
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