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

        // migracja struktur plików
        migratePlayerDataFolder();
        migratePasswordSection();
        migrateInventorySection();
        migrateSpawnSection();
    }

    private void migratePasswordSection() {
        // 1. Wskazujemy na folder playerdata
        File playerDataFolder = new File(plugin.getDataFolder(), "player_data");
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
        File playerDataFolder = new File(plugin.getDataFolder(), "player_data");

        File oldInvFile = new File(playerDataFolder, "inventory_storage.yml");
        File newInvFile = new File(playerDataFolder, "inventory_data.yml");

        // KROK 1: Jeśli stary plik istnieje, a nowego jeszcze nie ma - zmieniamy nazwę
        if (oldInvFile.exists() && !newInvFile.exists()) {
            oldInvFile.renameTo(newInvFile);
        }

        // KROK 2: Konwersja formatu danych wewnątrz poprawnego pliku inventory_data.yml
        if (!newInvFile.exists()) {
            return; // Jeśli plik nie istnieje, nie ma czego konwertować
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(newInvFile);
        java.util.Set<String> rootKeys = config.getKeys(false);
        boolean migrated = false;

        for (String key : rootKeys) {
            // Ignorujemy poprawną nową sekcję
            if (key.equalsIgnoreCase("inventory")) {
                continue;
            }

            // Jeśli kluczem głównym jest stare UUID (sekcja konfiguracyjna)
            if (config.isConfigurationSection(key)) {
                // Przepisujemy stare UUID pod strukturę inventory.<UUID>
                config.set("inventory." + key, config.getConfigurationSection(key));

                // Usuwamy stary wpis z roota pliku
                config.set(key, null);
                migrated = true;
            }
        }

        // Jeśli zmieniliśmy format chociaż jednego gracza, zapisujemy zmiany na dysk
        if (migrated) {
            try {
                config.save(newInvFile);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void migrateSpawnSection() {
        // 1. Definiujemy stary plik i folder (AstraLogin/spawns/locations.yml)
        File oldDir = new File(plugin.getDataFolder(), "spawns");
        File oldFile = new File(oldDir, "locations.yml");

        // Jeśli starego pliku nie ma, nic nie robimy (migracja już się kiedyś odbyła)
        if (!oldFile.exists()) {
            return;
        }

        // Ładujemy stare dane do pamięci RAM
        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);

        // 2. Przygotowujemy nowe foldery docelowe
        File globalDir = new File(plugin.getDataFolder(), "global_data");
        File playerDir = new File(plugin.getDataFolder(), "player_data");

        if (!globalDir.exists()) globalDir.mkdirs();
        if (!playerDir.exists()) playerDir.mkdirs();

        // Definiujemy nowe pliki
        File newSpawnsFile = new File(globalDir, "spawns.yml");
        File newPlayerDataFile = new File(playerDir, "locations_data.yml");

        // Ładujemy ich konfiguracje (nawet jeśli są puste)
        FileConfiguration newSpawnsConfig = YamlConfiguration.loadConfiguration(newSpawnsFile);
        FileConfiguration newPlayerDataConfig = YamlConfiguration.loadConfiguration(newPlayerDataFile);

        boolean changedSpawns = false;
        boolean changedPlayers = false;

        // 3. Migracja sekcji administracyjnej (spawns)
        if (oldConfig.contains("spawns")) {
            newSpawnsConfig.set("spawns", oldConfig.getConfigurationSection("spawns"));
            changedSpawns = true;
        }

        // 4. Migracja sekcji graczy (last_locations)
        if (oldConfig.contains("last_locations")) {
            newPlayerDataConfig.set("last_locations", oldConfig.getConfigurationSection("last_locations"));
            changedPlayers = true;
        }

        // 5. Zapisujemy nowe pliki na dysk tylko jeśli zostały zmodyfikowane
        try {
            if (changedSpawns) newSpawnsConfig.save(newSpawnsFile);
            if (changedPlayers) newPlayerDataConfig.save(newPlayerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 6. Bezpieczne sprzątanie po migracji
        if (oldFile.delete()) {
            // Jeśli stary folder 'spawns' jest pusty, usuwamy go, żeby nie śmiecił adminom w FTP
            File[] files = oldDir.listFiles();
            if (files == null || files.length == 0) {
                oldDir.delete();
            }
        }
    }

    public void migratePlayerDataFolder() {
        File oldFolder = new File(plugin.getDataFolder(), "playerdata");
        File newFolder = new File(plugin.getDataFolder(), "player_data");

        // Jeśli stary istnieje, a nowego jeszcze nie ma – po prostu zmieniamy nazwę
        if (oldFolder.exists() && !newFolder.exists()) {
            oldFolder.renameTo(newFolder);
        }
    }
}