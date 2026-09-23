package pl.dawcou.astralogin.auth.passwords;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.security.SecureRandom;
import java.util.UUID;

//--------------------------------------------------
// Menedżer kodów PIN z wykorzystaniem PlayerDataManager
//--------------------------------------------------
public class PINManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private static final SecureRandom secureRandom = new SecureRandom();

    public PINManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public String generatePIN(int length) {
        int bound = (int) Math.pow(10, length);
        int pinNumber = secureRandom.nextInt(bound);
        return String.format("%0" + length + "d", pinNumber);
    }

    public void savePIN(UUID uuid, String pin) {
        playerDataManager.set(uuid, "auth.pin", pin);
    }

    public String getPIN(UUID uuid) {
        return playerDataManager.getString(uuid, "auth.pin");
    }

    public boolean hasPIN(UUID uuid) {
        return playerDataManager.has(uuid, "auth.pin");
    }

    public void deletePIN(UUID uuid) {
        playerDataManager.remove(uuid, "auth.pin");
    }
}