package pl.dawcou.astralogin.auth.security.suspicious;

import pl.dawcou.astralogin.AstraLogin;

import java.util.UUID;

public class SuspiciousManager {

    private final AstraLogin plugin;
    private static final String DATA_KEY = "account.suspicious-level";

    public SuspiciousManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * Pobiera aktualny poziom podejrzliwości gracza
     */
    public SuspicionLevel getSuspicionLevel(UUID uuid) {
        if (uuid == null) return SuspicionLevel.NONE;

        int rawLevel = plugin.getPlayerDataManager().getInt(uuid, DATA_KEY, 0);
        return SuspicionLevel.fromLevel(rawLevel);
    }

    /**
     * Ustawia konkretny poziom podejrzliwości
     */
    public void setSuspicionLevel(UUID uuid, SuspicionLevel level) {
        if (uuid == null || level == null) return;

        if (level == SuspicionLevel.NONE) {
            clearSuspicion(uuid);
            return;
        }

        plugin.getPlayerDataManager().set(uuid, DATA_KEY, level.getLevel());
    }

    /**
     * Podnosi poziom podejrzliwości gracza o 1 poziom wyżej (maksymalnie do HIGH)
     */
    public void incrementSuspicion(UUID uuid) {
        if (uuid == null) return;

        SuspicionLevel current = getSuspicionLevel(uuid);
        int nextLevelInt = current.getLevel() + 1;

        // Pobieramy najwyższy dostępny poziom (HIGH = 3)
        if (nextLevelInt <= SuspicionLevel.HIGH.getLevel()) {
            SuspicionLevel nextLevel = SuspicionLevel.fromLevel(nextLevelInt);
            setSuspicionLevel(uuid, nextLevel);
        }
    }

    /**
     * Obniża poziom podejrzliwości gracza o 1 poziom niżej (minimalnie do NONE)
     */
    public void decrementSuspicion(UUID uuid) {
        if (uuid == null) return;

        SuspicionLevel current = getSuspicionLevel(uuid);
        int prevLevelInt = current.getLevel() - 1;

        // Pobieramy najniższy dostępny poziom (NONE = 0)
        if (prevLevelInt >= SuspicionLevel.NONE.getLevel()) {
            SuspicionLevel prevLevel = SuspicionLevel.fromLevel(prevLevelInt);
            setSuspicionLevel(uuid, prevLevel);
        }
    }

    /**
     * Czyści poziom podejrzliwości gracza
     */
    public void clearSuspicion(UUID uuid) {
        if (uuid == null) return;
        plugin.getPlayerDataManager().remove(uuid, DATA_KEY);
    }

    /**
     * Sprawdza czy gracz jest w jakimkolwiek stopniu podejrzany
     */
    public boolean isSuspicious(UUID uuid) {
        return getSuspicionLevel(uuid) != SuspicionLevel.NONE;
    }

    /**
     * Sprawdza czy poziom podejrzliwości gracza wynosi co najmniej podany poziom
     */
    public boolean hasAtLeast(UUID uuid, SuspicionLevel requiredLevel) {
        return getSuspicionLevel(uuid).getLevel() >= requiredLevel.getLevel();
    }
}