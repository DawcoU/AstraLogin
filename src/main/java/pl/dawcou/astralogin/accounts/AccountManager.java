package pl.dawcou.astralogin.accounts;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class AccountManager {

    private final AstraLogin plugin;
    private File configFile;
    private FileConfiguration accountsConfig;

    public AccountManager(AstraLogin plugin) {
        this.plugin = plugin;
        setup();
    }

    /**
     * Tworzy folder data/global oraz plik accounts.yml, jeśli nie istnieją,
     * a następnie ładuje je do pamięci.
     */
    public void setup() {
        File globalDataDir = new File(plugin.getDataFolder(), "data/global");
        if (!globalDataDir.exists()) {
            globalDataDir.mkdirs();
        }

        configFile = new File(globalDataDir, "accounts.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        accountsConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Zwraca aktualną konfigurację kont.
     */
    public FileConfiguration getConfig() {
        return accountsConfig;
    }

    /**
     * Zapisuje konfigurację na dysk asynchronicznie, aby nie blokować głównego wątku serwera.
     */
    public void saveConfig() {
        plugin.getSchedulerManager().runAsync(() -> {
            synchronized (configFile) { // Synchronizacja, żeby uniknąć uszkodzenia pliku przy wielu zapisach naraz
                try {
                    accountsConfig.save(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Rejestruje nowe konto lub odświeża dane po ponownej rejestracji (np. po resetowaniu hasła).
     */
    public void recordRegister(UUID uuid, String name, String ip) {
        String path = "accounts." + uuid.toString() + ".";
        accountsConfig.set(path + "last-known-name", name);
        accountsConfig.set(path + "last-ip", ip);
        accountsConfig.set(path + "register-date", getCurrentDateTime());
        accountsConfig.set(path + "last-login-date", getCurrentDateTime());
        accountsConfig.set(path + "is-registered", true);
        saveConfig();
    }

    /**
     * Aktualizuje IP oraz datę ostatniego zalogowania przy udanej komendzie /login.
     */
    public void recordLogin(UUID uuid, String name, String ip) {
        String path = "accounts." + uuid.toString() + ".";
        accountsConfig.set(path + "last-known-name", name); // Przy okazji aktualizujemy nick, gdyby gracz zmienił go
        accountsConfig.set(path + "last-ip", ip);
        accountsConfig.set(path + "last-login-date", getCurrentDateTime());
        saveConfig();
    }

    /**
     * Wywoływane przy komendzie administracyjnej /zresetujhaslo.
     * Zmienia flagę na false, informując system, że przy następnym wejściu
     * należy nadpisać datę rejestracji.
     */
    public void invalidateRegistration(UUID uuid) {
        String path = "accounts." + uuid.toString() + ".";
        if (accountsConfig.contains(path + "is-registered")) {
            accountsConfig.set(path + "is-registered", false);
            saveConfig();
        }
    }

    /**
     * Pomocnicza metoda zwracająca ładnie sformatowaną datę i godzinę.
     */
    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Całkowicie usuwa gracza z pliku accounts.yml (czyszczenie martwych danych).
     */
    public void purgeAccountData(UUID uuid) {
        String path = "accounts." + uuid.toString();
        if (accountsConfig.contains(path)) {
            accountsConfig.set(path, null);
            saveConfig();
        }
    }

    /**
     * Pobiera nick gracza z danych konta
     */
    public String getPlayerName(String uuid) {
        String path = "accounts." + uuid + ".last-known-name";
        return accountsConfig.getString(path);
    }

    public String getRegisteredNameIgnoreCase(String inputName) {
        if (accountsConfig == null) {
            return null;
        }

        ConfigurationSection accountsSection = accountsConfig.getConfigurationSection("accounts");
        if (accountsSection == null) {
            return null;
        }

        // Pętla przechodzi po każdym UUID (np. ea6a445b-cda3-3f77-baab-9760c1e58202)
        for (String uuidKey : accountsSection.getKeys(false)) {
            String savedName = accountsSection.getString(uuidKey + ".last-known-name");

            if (savedName != null && savedName.equalsIgnoreCase(inputName)) {
                return savedName; // Zwróci dokładny zarejestrowany nick, np. "DawcoU"
            }
        }

        return null; // Brak gracza w pliku
    }
}