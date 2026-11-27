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

import java.util.UUID;

//--------------------------------------------------
// Menedżer spawnów oraz ostatnich lokalizacji graczy
//--------------------------------------------------
public class SpawnManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final GlobalDataManager globalDataManager;

    public SpawnManager(AstraLogin plugin, PlayerDataManager playerDataManager, GlobalDataManager globalDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.globalDataManager = globalDataManager;
    }

    public void setSpawn(SpawnType type, Player p) {
        Location loc = p.getLocation();

        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns == null) {
            globalSpawns = new JsonObject();
        }

        globalSpawns.add(type.getKey(), serializeLocation(loc));
        globalDataManager.set("spawns", globalSpawns);
    }

    public void delSpawn(SpawnType type) {
        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns != null && globalSpawns.has(type.getKey())) {
            globalSpawns.remove(type.getKey());
            globalDataManager.set("spawns", globalSpawns);
        }
    }

    public void teleport(Player p, SpawnType type) {
        Location loc = getSpawnLocation(type);

        if (loc != null) {
            plugin.getSchedulerManager().teleport(p, loc);
            return;
        }

        if (type == SpawnType.AFTER_LOGIN) {
            plugin.getSchedulerManager().teleport(p, p.getWorld().getSpawnLocation());
        }
    }

    public boolean hasSpawn(SpawnType type) {
        return getSpawnLocation(type) != null;
    }

    public void saveLastLocation(Player p) {
        Location loc = p.getLocation();
        UUID uuid = p.getUniqueId();

        playerDataManager.set(uuid, "player.location", serializeLocation(loc));
    }

    public void teleportToLastLocation(Player p) {
        UUID uuid = p.getUniqueId();
        Location loc = loadLastLocationFromPlayer(uuid);

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
        playerDataManager.remove(uuid, "player.location");
    }

    private Location getSpawnLocation(SpawnType type) {
        JsonObject globalSpawns = globalDataManager.getJsonObject("spawns");
        if (globalSpawns != null && globalSpawns.has(type.getKey())) {
            JsonElement element = globalSpawns.get(type.getKey());
            if (element != null && element.isJsonObject()) {
                return deserializeLocation(element.getAsJsonObject());
            }
        }
        return null;
    }

    private Location loadLastLocationFromPlayer(UUID uuid) {
        JsonElement element = playerDataManager.getElement(uuid, "player.location");
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        return deserializeLocation(element.getAsJsonObject());
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

    //--------------------------------------------------
    // Deserializes location with safe numeric fallbacks
    //--------------------------------------------------
    private Location deserializeLocation(JsonObject json) {
        if (!json.has("world") || !json.get("world").isJsonPrimitive() || !json.get("world").getAsJsonPrimitive().isString()) {
            return null;
        }

        String worldName = json.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = getDoubleOrDefault(json, "x", 0.0);
        double y = getDoubleOrDefault(json, "y", 64.0);
        double z = getDoubleOrDefault(json, "z", 0.0);
        float yaw = getFloatOrDefault(json, "yaw", 0.0f);
        float pitch = getFloatOrDefault(json, "pitch", 0.0f);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
        if (!json.has(key) || json.get(key).isJsonNull()) return defaultValue;
        try {
            return json.get(key).getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private float getFloatOrDefault(JsonObject json, String key, float defaultValue) {
        if (!json.has(key) || json.get(key).isJsonNull()) return defaultValue;
        try {
            return json.get(key).getAsFloat();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}