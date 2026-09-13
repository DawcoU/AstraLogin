package pl.dawcou.astralogin.file.converters;

import pl.dawcou.astralogin.AstraLogin;

public class MigrationManager {

    private final AstraLogin plugin;
    private final ConfigConverter configConverter;
    private final FilesConverter filesConverter;

    public MigrationManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.configConverter = new ConfigConverter(plugin);
        this.filesConverter = new FilesConverter(plugin);
    }

    public void migrate() {
        boolean filesNeedMigration = filesConverter.needsMigration();
        boolean configNeedsMigration = configConverter.needsMigration();

        // Jeśli żaden plik nie wymaga migracji, kończymy bez tworzenia backupu
        if (!filesNeedMigration && !configNeedsMigration) {
            return;
        }

        // Wykonujemy JEDEN backup z flagą ignoreInterval = true przed rozpoczęciem jakichkolwiek migracji
        plugin.getBackupManager().createBackup(true);

        if (filesNeedMigration) {
            filesConverter.runAllMigrations();
        }

        if (configNeedsMigration) {
            configConverter.runAllMigrations();
        }
    }
}