package pl.dawcou.astralogin.file.converters.file;

public interface FileMigration {

    boolean needsMigration();

    void migrate();
}