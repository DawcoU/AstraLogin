package pl.dawcou.astralogin.auth.password;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.security.SecureRandom;
import java.util.UUID;

public class PINManager {

    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private static final SecureRandom secureRandom = new SecureRandom();

    public PINManager(AstraLogin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public String generatePIN(int length) {
        int bound = 1;
        for (int i = 0; i < length; i++) bound *= 10;

        String pattern = "%%0%dd".formatted(length);
        return String.format(pattern, secureRandom.nextInt(bound));
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