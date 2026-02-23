package pl.dawcou.astralogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class PasswordManager {

    private final File file;
    private final FileConfiguration config;

    public PasswordManager(AstraLogin plugin) {
        // 1. Tworzymy główny folder pluginu (AstraLogin)
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdir();

        // 2. Tworzymy folder 'playerdata' wewnątrz folderu pluginu
        File dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) dataDir.mkdir();

        // 3. Ustawiamy plik passwords.yml w nowym podfolderze
        this.file = new File(dataDir, "passwords.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // Reszta metod (zapiszHaslo, getHaslo, usunKonto) zostaje bez zmian!
    public void zapiszHaslo(String uuid, String haslo) {
        config.set("players." + uuid, haslo);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public String getHaslo(String uuid) {
        return config.getString("players." + uuid);
    }

    public boolean maHaslo(String uuid) {
        return config.contains("players." + uuid);
    }

    public void usunKonto(String uuid) {
        config.set("players." + uuid, null);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}