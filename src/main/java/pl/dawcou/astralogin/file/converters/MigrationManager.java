package pl.dawcou.astralogin.file.converters;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.file.converters.config.ConfigMigration;
import pl.dawcou.astralogin.file.converters.file.migrations.*;
import pl.dawcou.astralogin.file.converters.file.FileMigration;

import java.util.ArrayList;
import java.util.List;

public class MigrationManager {

    private final AstraLogin plugin;
    private final List<FileMigration> fileMigrations;

    public MigrationManager(AstraLogin plugin) {
        this.plugin = plugin;

        this.fileMigrations = List.of(
                new PasswordSectionFileMigration(plugin),
                new InventorySectionFileMigration(plugin),
                new SpawnSectionFileMigration(plugin),
                new DataFoldersFileMigration(plugin),
                new YamlToJsonFileMigration(plugin),
                new PlayerJsonStructureFileMigration(plugin),
                new ConfigMigration(plugin)
        );
    }

    public void migrate() {
        List<FileMigration> pendingFileMigrations = new ArrayList<>();

        for (FileMigration fileMigration : fileMigrations) {
            try {
                if (fileMigration.needsMigration()) {
                    pendingFileMigrations.add(fileMigration);
                }
            } catch (Exception e) {
                plugin.getLogger().severe(
                        "Failed to check fileMigration: "
                                + fileMigration.getClass().getSimpleName()
                );
                e.printStackTrace();

                // Nie uruchamiamy częściowej migracji, jeśli nie możemy
                // prawidłowo ustalić, które migracje są wymagane.
                return;
            }
        }

        if (pendingFileMigrations.isEmpty()) {
            return;
        }

        plugin.getLogger().info(
                "Found " + pendingFileMigrations.size()
                        + " pending migration(s)."
        );

        plugin.getBackupManager().createBackup(true);

        for (FileMigration fileMigration : pendingFileMigrations) {
            String name = fileMigration.getClass().getSimpleName();

            try {
                fileMigration.migrate();

            } catch (Exception e) {
                plugin.getLogger().severe(
                        "FileMigration failed: " + name
                );
                e.printStackTrace();
            }
        }
    }
}