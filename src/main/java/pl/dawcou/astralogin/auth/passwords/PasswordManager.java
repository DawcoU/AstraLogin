package pl.dawcou.astralogin.auth.passwords;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.util.UUID;

//--------------------------------------------------
// Menedżer haseł graczy z wykorzystaniem PlayerDataManager
//--------------------------------------------------
public class PasswordManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final PasswordHasher passwordHasher;

    public PasswordManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.passwordHasher = new PasswordHasher(plugin);
    }

    public PasswordHasher getPasswordHasher() {
        return passwordHasher;
    }

    public void savePassword(UUID uuid, String password) {
        playerDataManager.set(uuid, "auth.password", password);
    }

    public String getPassword(UUID uuid) {
        return playerDataManager.getString(uuid, "auth.password");
    }

    public boolean isRegistered(UUID uuid) {
        return playerDataManager.has(uuid, "auth.password");
    }

    public void deletePassword(UUID uuid) {
        playerDataManager.remove(uuid, "auth.password");
    }
}