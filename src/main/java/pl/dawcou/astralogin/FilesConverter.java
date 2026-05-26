package pl.dawcou.astralogin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class FilesConverter {

    private final AstraLogin plugin;

    public FilesConverter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    // Główna metoda, która zarządza wszystkimi konwersjami
    public void runAllMigrations() {

        // migracja struktury haseł wewnątrz pliku passwords.yml
        migratePasswordSection();
        migrateInventorySection();
    }

    private void migratePasswordSection() {
        // 1. Wskazujemy na folder playerdata
        File playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        File passFile = new File(playerDataFolder, "passwords.yml");

        if (!passFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(passFile);

        // 2. Sprawdzamy starą sekcję
        if (config.isConfigurationSection("players")) {
            ConfigurationSection oldSection = config.getConfigurationSection("players");

            // Pobieramy lub tworzymy nową sekcję "passwords"
            ConfigurationSection newSection = config.getConfigurationSection("passwords");
            if (newSection == null) {
                newSection = config.createSection("passwords");
            }

            // 3. Przenosimy dane (UUID: Hash)
            for (String uuid : oldSection.getKeys(false)) {
                newSection.set(uuid, oldSection.get(uuid));
            }

            // 4. Czyścimy starą sekcję "players"
            config.set("players", null);

            // 5. Zapisujemy z powrotem w playerdata/passwords.yml
            try {
                config.save(passFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void migrateInventorySection() {
        File playerDataFolder = new File(plugin.getDataFolder(), "playerdata");

        File oldInvFile = new File(playerDataFolder, "inventory_storage.yml");
        File newInvFile = new File(playerDataFolder, "inventory_data.yml");

        // 2. Jeśli stary plik istnieje, a nowego jeszcze nie ma - zmieniamy nazwę
        if (oldInvFile.exists() && !newInvFile.exists()) {
            oldInvFile.renameTo(newInvFile);
        }
    }
}