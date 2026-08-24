package pl.dawcou.astralogin.file.converters;

import pl.dawcou.astralogin.auth.AstraLogin;

public class MigrationManager {

    private final ConfigConverter configConverter;
    private final FilesConverter filesConverter;

    public MigrationManager(AstraLogin plugin) {
        this.configConverter = new ConfigConverter(plugin);
        this.filesConverter = new FilesConverter(plugin);
    }

    public void migrate() {
        filesConverter.runAllMigrations();
        configConverter.runAllMigrations();
    }
}