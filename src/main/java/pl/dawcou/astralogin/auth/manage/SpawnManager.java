package pl.dawcou.astralogin.auth.manage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnManager implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;

    // Plik 1: Globalne spawny serwera (w podfolderze data/global)
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

        // 1. Inicjalizacja globalnego folderu i pliku ze spawnami (AstraLogin/data/global/spawns.yml)
        File globalDir = new File(plugin.getDataFolder(), "data/global");
        if (!globalDir.exists()) {
            globalDir.mkdirs();
        }

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

        // 2. Inicjalizacja pliku z danymi graczy (AstraLogin/data/players/locations_data.yml)
        File dataDir = new File(plugin.getDataFolder(), "data/players");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        playerDataFile = new File(dataDir, "locations_data.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Ładujemy konfiguracje i odpalamy cache
        reload();
    }

    // --- SEKCJA SPAWNÓW SERWEROWYCH (data/global/spawns.yml) ---
    public void setSpawn(String type, Player p) {
        Location loc = p.getLocation();
        spawnsCache.put(type, loc);

        String path = "spawns." + type;
        serializeLocation(spawnsConfig, path, loc);
        saveSpawns();
    }

    public void delSpawn(String type) {
        spawnsCache.remove(type);
        spawnsConfig.set("spawns." + type, null);
        saveSpawns();
    }

    public void teleport(Player p, String type) {
        Location loc = spawnsCache.get(type);
        if (loc == null) return;
        p.teleport(loc);
    }

    public boolean hasSpawn(String type) {
        return spawnsCache.containsKey(type);
    }

    // --- SEKCJA LOKALIZACJI GRACZY (playerdata/locations_data.yml) ---
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
            teleport(p, "after_login");
            return;
        }

        try {
            p.teleport(loc);
        } catch (Exception e) {
            e.printStackTrace();
            teleport(p, "after_login");
            plugin.getNoticeManager().sendPlayerLocationReadError(p.getName());
        }
    }

    // --- METODY POMOCNICZE ---

    public void reload() {
        // Ładowanie plików z dysku
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        // Czyszczenie starego cache
        spawnsCache.clear();
        lastLocationsCache.clear();

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

    public void deletePlayerSpawn(String uuidString) {
        // 1. Bezkompromisowe czyszczenie z pamięci RAM (Cache)
        lastLocationsCache.remove(uuidString);

        // 2. Czyszczenie sekcji z pliku konfiguracyjnego
        String path = "last_locations." + uuidString;
        if (playerDataConfig.contains(path)) {
            playerDataConfig.set(path, null);

            // 3. Zapisujemy zaktualizowany plik na dysku
            savePlayerData();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (command.getName().equalsIgnoreCase("loginspawn") || command.getName().equalsIgnoreCase("spawnlogowania")) {
            if (p == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("setspawn")) {
                if (!p.hasPermission("astralogin.spawn.set")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.login-usage"));
                    return true;
                }

                String type = args[1].toLowerCase();

                // Sprawdzamy czy typ jest poprawny
                if (!type.equals("before_login") && !type.equals("after_login")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.invalid-type"));
                    return true;
                }

                // 1. LOGIKA POTWIERDZENIA NADPISANIA
                boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                if (plugin.getSpawnManager().hasSpawn(type) && !confirmed) {
                    String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn.exists").replace("%type%", type);
                    String btnTextStr = plugin.getLanguageManager().getMessage("spawn.overwrite-button");
                    String hoverTextStr = plugin.getLanguageManager().getMessage("spawn.overwrite-hover").replace("%type%", type);

                    Component baseMsg = LegacyComponentSerializer.legacySection()
                            .deserialize(baseMsgStr + " ");

                    Component confirmBtn = LegacyComponentSerializer.legacySection()
                            .deserialize(btnTextStr)
                            .clickEvent(ClickEvent.runCommand("/loginspawn setspawn " + type + " confirm"))
                            .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                    plugin.getAdventure().player(p).sendMessage(baseMsg.append(confirmBtn));
                    return true;
                }

                // 2. WŁAŚCIWE USTAWIENIE SPAWNU
                plugin.getSpawnManager().setSpawn(type, p);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.set-success").replace("%type%", type));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("delspawn")) {
                if (!p.hasPermission("astralogin.spawn.delete")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.login-usage"));
                    return true;
                }

                String type = args[1].toLowerCase();

                // 1. LOGIKA POTWIERDZENIA
                boolean confirmed = (args.length > 2 && args[2].equalsIgnoreCase("confirm"));

                if (confirmed) {
                    if (plugin.getSpawnManager().hasSpawn(type)) {
                        plugin.getSpawnManager().delSpawn(type);
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.deleted-success").replace("%type%", type));
                    } else {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.does-not-exist").replace("%type%", type));
                    }
                    return true;
                }

                // 2. SPRAWDZAMY CZY W OGÓLE ISTNIEJE
                if (!plugin.getSpawnManager().hasSpawn(type)) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.does-not-exist").replace("%type%", type));
                    return true;
                }

                // 3. POKAZYWANIE PRZYCISKU
                String baseMsgStr = plugin.getLanguageManager().getWithPrefix("spawn.delete-confirm").replace("%type%", type);
                String btnTextStr = plugin.getLanguageManager().getMessage("spawn.delete-button");
                String hoverTextStr = plugin.getLanguageManager().getMessage("spawn.delete-hover").replace("%type%", type);

                Component baseMsg = LegacyComponentSerializer.legacySection()
                        .deserialize(baseMsgStr + " ");

                Component confirmBtn = LegacyComponentSerializer.legacySection()
                        .deserialize(btnTextStr)
                        .clickEvent(ClickEvent.runCommand("/loginspawn delspawn " + type + " confirm"))
                        .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

                plugin.getAdventure().player(p).sendMessage(baseMsg.append(confirmBtn));
                return true;
            }

            sender.sendMessage(plugin.getLanguageManager().getWithPrefix("spawn.usage"));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> hints = new java.util.ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("spawnlogowania") || cmd.equalsIgnoreCase("loginspawn")) {
            if (args.length == 1) {
                if (sender.hasPermission("astralogin.spawn.set")) {
                    hints.add("setspawn");
                }
                if (sender.hasPermission("astralogin.spawn.delete")) {
                    hints.add("delspawn");
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn"))) {
                hints.add("before_login");
                hints.add("after_login");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return hints.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(java.util.stream.Collectors.toList());
    }
}