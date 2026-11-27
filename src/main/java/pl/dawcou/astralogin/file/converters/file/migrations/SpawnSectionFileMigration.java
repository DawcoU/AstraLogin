package pl.dawcou.astralogin.file.converters.file.migrations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.file.FileMigration;
import pl.dawcou.astralogin.file.converters.file.MigrationUtils;

import java.io.File;

public class SpawnSectionFileMigration implements FileMigration {

    private final AstraLogin plugin;
    private final File oldFile;

    public SpawnSectionFileMigration(AstraLogin plugin) {
        this.plugin = plugin;
        this.oldFile = new File(
                plugin.getDataFolder(),
                "spawns/locations.yml"
        );
    }

    @Override
    public boolean needsMigration() {
        return oldFile.exists();
    }

    @Override
    public void migrate() {
        if (!oldFile.exists()) {
            return;
        }

        plugin.getNoticeManager().sendMigrationNotice(
                "spawns/locations.yml",
                "global_data/spawns.yml & player_data/locations_data.yml"
        );

        FileConfiguration oldConfig =
                YamlConfiguration.loadConfiguration(oldFile);

        ConfigurationSection spawns =
                oldConfig.getConfigurationSection("spawns");

        ConfigurationSection lastLocations =
                oldConfig.getConfigurationSection("last_locations");

        if (spawns == null && lastLocations == null) {
            plugin.getLogger().warning(
                    "Spawn migration skipped: locations.yml contains no recognized sections."
            );
            return;
        }

        File globalDir = new File(plugin.getDataFolder(), "global_data");
        File playerDir = new File(plugin.getDataFolder(), "player_data");

        if (MigrationUtils.ensureDirectory(plugin, globalDir)
                || MigrationUtils.ensureDirectory(plugin, playerDir)) {
            return;
        }

        File newSpawnsFile = new File(globalDir, "spawns.yml");
        File newPlayerDataFile = new File(playerDir, "locations_data.yml");

        FileConfiguration newSpawnsConfig =
                YamlConfiguration.loadConfiguration(newSpawnsFile);

        FileConfiguration newPlayerDataConfig =
                YamlConfiguration.loadConfiguration(newPlayerDataFile);

        boolean changedSpawns = false;
        boolean changedPlayers = false;

        if (spawns != null) {
            if (newSpawnsConfig.isConfigurationSection("spawns")) {
                ConfigurationSection existing =
                        newSpawnsConfig.getConfigurationSection("spawns");

                for (String key : spawns.getKeys(false)) {
                    if (existing != null && existing.contains(key)) {
                        plugin.getLogger().warning(
                                "Spawn migration skipped existing spawn: " + key
                        );
                        continue;
                    }

                    newSpawnsConfig.set("spawns." + key, spawns.get(key));
                    changedSpawns = true;
                }
            } else {
                newSpawnsConfig.set("spawns", spawns);
                changedSpawns = true;
            }
        }

        if (lastLocations != null) {
            ConfigurationSection existing =
                    newPlayerDataConfig.getConfigurationSection("last_locations");

            for (String uuid : lastLocations.getKeys(false)) {
                if (existing != null && existing.contains(uuid)) {
                    plugin.getLogger().warning(
                            "Location migration skipped existing player: " + uuid
                    );
                    continue;
                }

                newPlayerDataConfig.set(
                        "last_locations." + uuid,
                        lastLocations.get(uuid)
                );
                changedPlayers = true;
            }
        }

        boolean savedSpawns = !changedSpawns
                || MigrationUtils.saveConfig(plugin, newSpawnsConfig, newSpawnsFile);

        boolean savedPlayers = !changedPlayers
                || MigrationUtils.saveConfig(
                plugin,
                newPlayerDataConfig,
                newPlayerDataFile
        );

        if (!savedSpawns || !savedPlayers) {
            return;
        }

        if (MigrationUtils.deleteFile(plugin, oldFile)) {
            MigrationUtils.deleteEmptyDirectory(oldFile.getParentFile());

            plugin.getNoticeManager().sendSuccessMigrationNotice(
                    "spawns/locations.yml"
            );
        }
    }
}