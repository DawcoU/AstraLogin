package pl.dawcou.astralogin.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import pl.dawcou.astralogin.auth.security.suspicious.SuspicionLevel;

import java.util.UUID;

public interface AstraLoginAPI {

    // --- Stan gracza ---
    boolean isRegistered(@NonNull UUID uuid);
    boolean isLogged(@NonNull UUID uuid);
    boolean hasTwoFactor(@NonNull UUID uuid);
    boolean hasPin(@NonNull UUID uuid);
    boolean hasMail(@NonNull UUID uuid);
    boolean isPremium(@NonNull UUID uuid);

    @Nullable String getLastIp(@NonNull UUID uuid);
    int getTrustScore(@NonNull String ip);

    @NonNull SuspicionLevel getSuspicionLevel(@NonNull UUID uuid);
    boolean isSuspicious(@NonNull UUID uuid);

    @Nullable String getMail(@NonNull UUID uuid);

    @Nullable String getRegisteredName(@NonNull UUID uuid);

    boolean hasActiveCaptcha(@NonNull Player p);

    boolean hasActiveSession(@NonNull UUID uuid, @NonNull String currentIp);
    boolean hasActive2FASession(@NonNull UUID uuid, @NonNull String currentIp);

    long getRegistrationDate(@NonNull UUID uuid);
    long getLastLoginDate(@NonNull UUID uuid);

    // --- Akcje i zarządzanie sesjami / kontem ---
    void forceLogin(@NonNull Player p);
    void forceLogout(@NonNull Player p);

    void unregister(@NonNull UUID uuid);

    void setTrustScore(@NonNull String ip, int score);
    void setSuspicionLevel(@NonNull UUID uuid, @NonNull SuspicionLevel level);

    void invalidateSession(@NonNull UUID uuid);
    void invalidate2FASession(@NonNull UUID uuid);

    void resetTwoFactor(@NonNull UUID uuid);

    void resetPin(@NonNull UUID uuid);

    void resetIp(@NonNull UUID uuid);
}