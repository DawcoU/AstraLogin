package pl.dawcou.astralogin.auth;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

//--------------------------------------------------
// Menedżer kont gracza wykorzystujący PlayerDataManager
//--------------------------------------------------
public class AccountManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;

    public AccountManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public void recordRegister(UUID uuid, String name) {
        String now = getCurrentDateTime();
        playerDataManager.set(uuid, "account.name", name);
        playerDataManager.set(uuid, "account.register-date", now);
        playerDataManager.set(uuid, "account.last-login-date", now);

        plugin.getGlobalDataManager().set("usermap.names." + name.toLowerCase(), name);
        plugin.getGlobalDataManager().set("usermap.uuids." + name.toLowerCase(), uuid.toString());
    }

    public void recordLogin(UUID uuid, String name) {
        playerDataManager.set(uuid, "account.name", name);
        playerDataManager.set(uuid, "account.last-login-date", getCurrentDateTime());

        plugin.getGlobalDataManager().set("usermap.names." + name.toLowerCase(), name);
        plugin.getGlobalDataManager().set("usermap.uuids." + name.toLowerCase(), uuid.toString());
    }

    public long getLastSecurityReminderTime(UUID uuid) {
        return playerDataManager.getLong(uuid, "account.last-security-reminder-timestamp", 0L);
    }

    public void setLastSecurityReminderTime(UUID uuid, long timestamp) {
        playerDataManager.set(uuid, "account.last-security-reminder-timestamp", timestamp);
    }

    public void purgeAccountData(UUID uuid) {
        String name = playerDataManager.getString(uuid, "account.name");

        if (name != null && !name.isEmpty()) {
            String lowerName = name.toLowerCase();
            plugin.getGlobalDataManager().remove("usermap.names." + lowerName);
            plugin.getGlobalDataManager().remove("usermap.uuids." + lowerName);
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    //--------------------------------------------------
    // Wyszukiwanie zarejestrowanego nicku (ignoruje wielkość liter)
    //--------------------------------------------------
    public String getRegisteredNameIgnoreCase(String inputName) {
        if (inputName == null || inputName.isEmpty()) {
            return null;
        }
        return plugin.getGlobalDataManager().getString("usermap.names." + inputName.toLowerCase());
    }

    //--------------------------------------------------
    // Pobieranie UUID gracza na podstawie nazwy (ignoruje wielkość liter)
    //--------------------------------------------------
    public UUID getUuidByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }

        String rawUuid = plugin.getGlobalDataManager().getString("usermap.uuids." + username.toLowerCase());
        if (rawUuid == null || rawUuid.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}