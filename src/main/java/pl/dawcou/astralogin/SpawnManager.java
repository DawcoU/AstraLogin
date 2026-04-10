package pl.dawcou.astralogin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class SpawnManager {

    private final AstraLogin plugin;
    private final File file;
    private FileConfiguration config;

    public SpawnManager(AstraLogin plugin) {
        this.plugin = plugin;
        // Tworzymy folder 'spawns' i plik 'locations.yml'
        File dir = new File(plugin.getDataFolder(), "spawns");
        if (!dir.exists()) dir.mkdirs();

        this.file = new File(dir, "locations.yml");
        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
            } catch (IOException e) {
                plugin.sendSpawnCreateError();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void setSpawn(String type, Player p) {
        Location loc = p.getLocation();
        String path = "spawns." + type;

        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());

        save();
    }

    public void delSpawn(String type) {
        config.set("spawns." + type, null);
        save();
    }

    public void teleport(Player p, String type) {
        String path = "spawns." + type;
        if (!config.contains(path)) return; // Brak spawna? Zostawiamy gracza w spokoju.

        String worldName = config.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch")
        );
        p.teleport(loc);
    }

    public void saveLastLocation(Player p) {
        Location loc = p.getLocation();
        // KONIECZNIE dodaj .toString(), żeby ścieżka była czystym tekstem
        String uuid = p.getUniqueId().toString();
        String path = "last_locations." + uuid;

        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", (double) loc.getYaw()); // Rzutujemy na double dla świętego spokoju
        config.set(path + ".pitch", (double) loc.getPitch());

        save();
    }

    public void teleportToLastLocation(Player p) {
        String uuid = p.getUniqueId().toString();
        String path = "last_locations." + uuid;

        if (!config.contains(path)) {
            teleport(p, "after_login");
            return;
        }

        try {
            String worldName = config.getString(path + ".world");
            if (worldName == null) {
                teleport(p, "after_login");
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                teleport(p, "after_login");
                return;
            }

            // Pobieramy kordy - ważne, żeby użyć getDouble
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            float pitch = (float) config.getDouble(path + ".pitch");

            Location loc = new Location(world, x, y, z, yaw, pitch);

            // Finalny teleport
            p.teleport(loc);

        } catch (Exception e) {
            // Jeśli cokolwiek pójdzie nie tak przy czytaniu (np. błąd formatu), lecisz na spawn
            teleport(p, "after_login");
            plugin.sendPlayerLocationReadError(p.getName());
        }
    }

    public boolean hasSpawn(String type) {
        // Sprawdzamy czy w pliku locations.yml (lub mapie) istnieje dany klucz
        return config.contains("spawns." + type);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.sendSpawnSaveError();
        }
    }
}