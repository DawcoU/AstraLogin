package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpawnManager {

    private final AstraLogin plugin;

    // Plik 1: Globalne spawny serwera (w podfolderze global_data)
    private final File spawnsFile;
    private FileConfiguration spawnsConfig;

    // Plik 2: Ostatnie lokalizacje graczy (w podfolderze playerdata)
    private final File playerDataFile;
    private FileConfiguration playerDataConfig;

    // Szybki Cache w RAM-ie zapewniający natychmiastowy dostęp bez czytania dysku
    private final Map<String, Location> spawnsCache = new HashMap<>();
    private final Map<String, Location> lastLocationsCache = new HashMap<>();

    public SpawnManager(AstraLogin plugin) {
        this.plugin = plugin;

        // 1. Inicjalizacja globalnego folderu i pliku ze spawnami (AstraLogin/global_data/spawns.yml)
        File globalDir = new File(plugin.getDataFolder(), "global_data");
        if (!globalDir.exists()) {
            globalDir.mkdirs();
        }

        this.spawnsFile = new File(globalDir, "spawns.yml");
        if (!this.spawnsFile.exists()) {
            try {
                this.spawnsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                plugin.getNoticeManager().sendSpawnCreateError();
            }
        }

        // 2. Inicjalizacja pliku z danymi graczy (AstraLogin/player_data/locations_data.yml)
        File dataDir = new File(plugin.getDataFolder(), "player_data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.playerDataFile = new File(dataDir, "locations_data.yml");
        if (!this.playerDataFile.exists()) {
            try {
                this.playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Ładujemy konfiguracje i odpalamy cache
        reload();
    }

    // --- SEKCJA SPAWNÓW SERWEROWYCH (global_data/spawns.yml) ---

    public void setSpawn(String type, Player p) {
        Location loc = p.getLocation();
        spawnsCache.put(type, loc); // Zapis do RAM-u

        String path = "spawns." + type;
        serializeLocation(spawnsConfig, path, loc);
        saveSpawns();
    }

    public void delSpawn(String type) {
        spawnsCache.remove(type); // Czyszczenie z RAM-u
        spawnsConfig.set("spawns." + type, null);
        saveSpawns();
    }

    public void teleport(Player p, String type) {
        Location loc = spawnsCache.get(type); // Pobieranie z RAM-u w czasie O(1)
        if (loc == null) return;
        p.teleport(loc);
    }

    public boolean hasSpawn(String type) {
        return spawnsCache.containsKey(type); // Błyskawiczne sprawdzenie w RAM-ie
    }

    // --- SEKCJA LOKALIZACJI GRACZY (playerdata/locations_data.yml) ---

    public void saveLastLocation(Player p) {
        Location loc = p.getLocation();
        String uuid = p.getUniqueId().toString();

        lastLocationsCache.put(uuid, loc); // Zapis do RAM-u

        String path = "last_locations." + uuid;
        serializeLocation(playerDataConfig, path, loc);
        savePlayerData();
    }

    public void teleportToLastLocation(Player p) {
        String uuid = p.getUniqueId().toString();
        Location loc = lastLocationsCache.get(uuid); // Wyciągamy z RAM-u

        if (loc == null) {
            teleport(p, "after_login");
            return;
        }

        try {
            p.teleport(loc);
        } catch (Exception e) {
            e.printStackTrace(); // Czerwona ściana tekstu w razie awarii świata
            teleport(p, "after_login");
            plugin.getNoticeManager().sendPlayerLocationReadError(p.getName());
        }
    }

    // --- METODY POMOCNICZE (CZYSZCZENIE I OPTYMALIZACJA KODU) ---

    public void reload() {
        // Ładowanie plików z dysku
        this.spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        this.playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        // Czyszczenie starego cache
        this.spawnsCache.clear();
        this.lastLocationsCache.clear();

        // Przebudowanie Cache dla spawnów serwera
        if (spawnsConfig.getConfigurationSection("spawns") != null) {
            for (String type : spawnsConfig.getConfigurationSection("spawns").getKeys(false)) {
                Location loc = deserializeLocation(spawnsConfig, "spawns." + type);
                if (loc != null) spawnsCache.put(type, loc);
            }
        }

        // Przebudowanie Cache dla lokalizacji graczy
        if (playerDataConfig.getConfigurationSection("last_locations") != null) {
            for (String uuid : playerDataConfig.getConfigurationSection("last_locations").getKeys(false)) {
                Location loc = deserializeLocation(playerDataConfig, "last_locations." + uuid);
                if (loc != null) lastLocationsCache.put(uuid, loc);
            }
        }
    }

    private void serializeLocation(FileConfiguration config, String path, Location loc) {
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", (double) loc.getYaw());
        config.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location deserializeLocation(FileConfiguration config, String path) {
        String worldName = config.getString(path + ".world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null; // Jeśli świat nie jest załadowany, ignorujemy

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveSpawns() {
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getNoticeManager().sendSpawnSaveError();
        }
    }

    private void savePlayerData() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deletePlayerSpawn(String uuidString) {
        // 1. Bezkompromisowe czyszczenie z pamięci RAM (Cache)
        this.lastLocationsCache.remove(uuidString);

        // 2. Czyszczenie sekcji z pliku konfiguracyjnego
        String path = "last_locations." + uuidString;
        if (this.playerDataConfig.contains(path)) {
            this.playerDataConfig.set(path, null);

            // 3. Zapisujemy zaktualizowany plik na dysku
            savePlayerData();
        }
    }
}