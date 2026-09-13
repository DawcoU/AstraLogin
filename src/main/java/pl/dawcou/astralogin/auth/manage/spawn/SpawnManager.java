package pl.dawcou.astralogin.auth.manage.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpawnManager {

    private final AstraLogin plugin;

    private final File spawnsFile;
    private FileConfiguration spawnsConfig;

    private final File playerDataFile;
    private FileConfiguration playerDataConfig;

    // Cache używający Enuma jako klucza
    private final Map<SpawnType, Location> spawnsCache = new HashMap<>();
    private final Map<String, Location> lastLocationsCache = new HashMap<>();

    public SpawnManager(AstraLogin plugin) {
        this.plugin = plugin;

        File globalDir = new File(plugin.getDataFolder(), "data/global");
        if (!globalDir.exists()) globalDir.mkdirs();

        spawnsFile = new File(globalDir, "spawns.yml");
        if (!spawnsFile.exists()) {
            try {
                spawnsFile.createNewFile();
                spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
                spawnsConfig.save(spawnsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) dataDir.mkdirs();

        playerDataFile = new File(dataDir, "locations_data.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reload();
    }

    // --- SEKCJA SPAWNÓW SERWEROWYCH ---

    public void setSpawn(SpawnType type, Player p) {
        Location loc = p.getLocation();
        spawnsCache.put(type, loc);

        String path = "spawns." + type.getKey();
        serializeLocation(spawnsConfig, path, loc);
        saveSpawns();
    }

    public void delSpawn(SpawnType type) {
        spawnsCache.remove(type);
        spawnsConfig.set("spawns." + type.getKey(), null);
        saveSpawns();
    }

    public void teleport(Player p, SpawnType type) {
        Location loc = spawnsCache.get(type);

        if (loc != null) {
            p.teleport(loc);
            return;
        }

        // Awaryjny fallback na spawn świata, jeśli after_login nie istnieje
        if (type == SpawnType.AFTER_LOGIN) {
            p.teleport(p.getWorld().getSpawnLocation());
        }
    }

    public boolean hasSpawn(SpawnType type) {
        return spawnsCache.containsKey(type);
    }

    // --- SEKCJA LOKALIZACJI GRACZY ---

    public void saveLastLocation(Player p) {
        Location loc = p.getLocation();
        String uuid = p.getUniqueId().toString();

        lastLocationsCache.put(uuid, loc);

        String path = "last_locations." + uuid;
        serializeLocation(playerDataConfig, path, loc);
        savePlayerData();
    }

    public void teleportToLastLocation(Player p) {
        String uuid = p.getUniqueId().toString();
        Location loc = lastLocationsCache.get(uuid);

        if (loc == null) {
            teleport(p, SpawnType.AFTER_LOGIN);
            return;
        }

        try {
            p.teleport(loc);
        } catch (Exception e) {
            plugin.getNoticeManager().sendPlayerLocationReadError(p.getName());
            teleport(p, SpawnType.AFTER_LOGIN);
        }
    }

    public void deletePlayerSpawn(String uuidString) {
        lastLocationsCache.remove(uuidString);

        String path = "last_locations." + uuidString;
        if (playerDataConfig.contains(path)) {
            playerDataConfig.set(path, null);
            savePlayerData();
        }
    }

    // --- METODY POMOCNICZE ---

    public void reload() {
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        spawnsCache.clear();
        lastLocationsCache.clear();

        if (spawnsConfig.getConfigurationSection("spawns") != null) {
            for (String key : spawnsConfig.getConfigurationSection("spawns").getKeys(false)) {
                SpawnType type = SpawnType.parse(key);
                if (type != null) {
                    Location loc = deserializeLocation(spawnsConfig, "spawns." + key);
                    if (loc != null) spawnsCache.put(type, loc);
                }
            }
        }

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
        if (world == null) return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveSpawns() {
        synchronized (spawnsConfig) {
            try {
                spawnsConfig.save(spawnsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void savePlayerData() {
        synchronized (playerDataConfig) {
            try {
                playerDataConfig.save(playerDataFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}