package pl.dawcou.astralogin.auth.manage.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.GlobalDataManager;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Menedżer spawnów oraz ostatnich lokalizacji graczy
//--------------------------------------------------
public class SpawnManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final GlobalDataManager globalDataManager;

    private final Map<SpawnType, Location> spawnsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocationsCache = new ConcurrentHashMap<>();

    public SpawnManager(AstraLogin plugin, PlayerDataManager playerDataManager, GlobalDataManager globalDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.globalDataManager = globalDataManager;

        reload();
    }

    public void setSpawn(SpawnType type, Player p) {
        Location loc = p.getLocation();
        spawnsCache.put(type, loc);

        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns == null) {
            globalSpawns = new JsonObject();
        }

        globalSpawns.add(type.getKey(), serializeLocation(loc));
        globalDataManager.set("spawns", globalSpawns);
    }

    public void delSpawn(SpawnType type) {
        spawnsCache.remove(type);

        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns != null && globalSpawns.has(type.getKey())) {
            globalSpawns.remove(type.getKey());
            globalDataManager.set("spawns", globalSpawns);
        }
    }

    public void teleport(Player p, SpawnType type) {
        Location loc = spawnsCache.get(type);

        if (loc != null) {
            plugin.getSchedulerManager().teleport(p, loc);
            return;
        }

        if (type == SpawnType.AFTER_LOGIN) {
            plugin.getSchedulerManager().teleport(p, p.getWorld().getSpawnLocation());
        }
    }

    public boolean hasSpawn(SpawnType type) {
        return spawnsCache.containsKey(type);
    }

    public void saveLastLocation(Player p) {
        Location loc = p.getLocation();
        UUID uuid = p.getUniqueId();

        lastLocationsCache.put(uuid, loc);

        // Zapisujemy całą lokalizację jednym obiektem JSON
        playerDataManager.set(uuid, "location", serializeLocation(loc));
    }

    public void teleportToLastLocation(Player p) {
        UUID uuid = p.getUniqueId();
        Location loc = lastLocationsCache.get(uuid);

        if (loc == null) {
            loc = loadLastLocationFromPlayer(uuid);
        }

        if (loc == null) {
            teleport(p, SpawnType.AFTER_LOGIN);
            return;
        }

        try {
            plugin.getSchedulerManager().teleport(p, loc);
        } catch (Exception e) {
            plugin.getNoticeManager().sendPlayerLocationReadError(p.getName());
            teleport(p, SpawnType.AFTER_LOGIN);
        }
    }

    public void deletePlayerSpawn(UUID uuid) {
        lastLocationsCache.remove(uuid);

        // Kasujemy cały obiekt "location" jednym wywołaniem publicznej metody remove
        playerDataManager.remove(uuid, "location");
    }

    public void reload() {
        spawnsCache.clear();
        lastLocationsCache.clear();

        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns != null) {
            for (Map.Entry<String, JsonElement> entry : globalSpawns.entrySet()) {
                SpawnType type = SpawnType.parse(entry.getKey());
                if (type != null && entry.getValue().isJsonObject()) {
                    Location loc = deserializeLocation(entry.getValue().getAsJsonObject());
                    if (loc != null) spawnsCache.put(type, loc);
                }
            }
        }
    }

    private Location loadLastLocationFromPlayer(UUID uuid) {
        if (!playerDataManager.has(uuid, "location.world")) return null;

        String worldName = playerDataManager.getString(uuid, "location.world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        try {
            double x = Double.parseDouble(playerDataManager.getString(uuid, "location.x"));
            double y = Double.parseDouble(playerDataManager.getString(uuid, "location.y"));
            double z = Double.parseDouble(playerDataManager.getString(uuid, "location.z"));
            float yaw = Float.parseFloat(playerDataManager.getString(uuid, "location.yaw"));
            float pitch = Float.parseFloat(playerDataManager.getString(uuid, "location.pitch"));

            Location loc = new Location(world, x, y, z, yaw, pitch);
            lastLocationsCache.put(uuid, loc);
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject serializeLocation(Location loc) {
        JsonObject json = new JsonObject();
        json.addProperty("world", loc.getWorld().getName());
        json.addProperty("x", loc.getX());
        json.addProperty("y", loc.getY());
        json.addProperty("z", loc.getZ());
        json.addProperty("yaw", loc.getYaw());
        json.addProperty("pitch", loc.getPitch());
        return json;
    }

    private Location deserializeLocation(JsonObject json) {
        if (!json.has("world") || json.get("world").isJsonNull()) return null;

        String worldName = json.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        float yaw = json.get("yaw").getAsFloat();
        float pitch = json.get("pitch").getAsFloat();

        return new Location(world, x, y, z, yaw, pitch);
    }
}