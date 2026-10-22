package pl.dawcou.astralogin.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface AstraLoginAPI {

    // --- Stan gracza ---
    boolean isRegistered(@NonNull UUID uuid);
    boolean isLogged(@NonNull UUID uuid);
    boolean hasTwoFactor(@NonNull UUID uuid);
    boolean isPremium(@NonNull UUID uuid);
    @Nullable String getLastIp(@NonNull UUID uuid);

    // --- Akcje i zarządzanie sesjami / kontem ---
    void forceLogin(@NonNull Player p);
    void forceLogout(@NonNull Player p);
    boolean hasActiveSession(@NonNull UUID uuid, @NonNull String currentIp);
    boolean hasActive2FASession(@NonNull UUID uuid, @NonNull String currentIp);
    void unregister(@NonNull UUID uuid);
}