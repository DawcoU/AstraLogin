package pl.dawcou.astralogin.auth.security.premium;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumManager {

    // --- ENUM STANÓW BYPASSU ---
    public enum BypassState {
        DENIED,        // Brak uwierzytelnienia / gracz non-premium / zablokowany bypass
        AUTHENTICATED  // Przeszedł pomyślnie szyfrowanie Mojang i ma pełny bypass
    }

    private static class StateEntry {
        private final BypassState state;
        private final long timestamp;

        public StateEntry(BypassState state) {
            this.state = state;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final AstraLogin plugin;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<String, Boolean> usernameCache = new ConcurrentHashMap<>();
    private final Map<String, Long> usernameCacheTime = new ConcurrentHashMap<>();

    // Jedna główna mapa zarządzająca stanem gracza
    private final Map<UUID, StateEntry> playerStates = new ConcurrentHashMap<>();

    public Map<String, Boolean> getUsernameCache() { return Collections.unmodifiableMap(usernameCache); }

    public PremiumManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void debug(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().info(message);
        }
    }

    public void debugWarning(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().warning(message);
        }
    }

    public void debugSevere(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().severe(message);
        }
    }

    public boolean isPremium(Player p) {
        UUID uuid = p.getUniqueId();

        String mode = plugin.getConfig().getString("security.auto-login.mode", "FULL");

        if (!premiumLoginRequirements(mode)) {
            return false;
        }

        if (!plugin.getPasswordManager().isRegistered(uuid)) {
            return false;
        }

        // Pełne logowanie premium
        if ("FULL".equalsIgnoreCase(mode)) {
            return isAuthenticated(uuid);
        }


        return isUsernamePremium(p.getName());
    }

    public boolean isUsernamePremium(String username) {
        String lowerName = username.toLowerCase(java.util.Locale.ROOT);

        Boolean cached = checkUsernameCache(lowerName);
        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AstraLogin/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            boolean premium = response.statusCode() == 200;

            usernameCache.put(lowerName, premium);
            usernameCacheTime.put(lowerName, System.currentTimeMillis());

            return premium;
        } catch (Exception e) {
            plugin.getNoticeManager().sendPremiumCheckError(username, e);
            return false;
        }
    }

    public Boolean checkUsernameCache(String lowerName) {
        Boolean cached = usernameCache.get(lowerName);
        Long cacheTime = usernameCacheTime.get(lowerName);

        if (cached != null && cacheTime != null) {
            long cacheAge = System.currentTimeMillis() - cacheTime;

            if (cacheAge < 12L * 60 * 60 * 1000L) {
                return cached;
            }
            usernameCache.remove(lowerName);
            usernameCacheTime.remove(lowerName);
        }
        return null;
    }

    public void cleanCache() {
        long now = System.currentTimeMillis();

        usernameCacheTime.forEach((name, time) -> {
            if (now - time >= 12L * 60 * 60 * 1000) {
                usernameCache.remove(name);
                usernameCacheTime.remove(name);
            }
        });

        // Czyścimy porzucone stany z pakietów (np. połączenia zerwane przed dołączeniem do świata > 30s)
        playerStates.forEach((uuid, entry) -> {
            if (now - entry.timestamp >= 30_000 && entry.state != BypassState.AUTHENTICATED) {
                playerStates.remove(uuid);
            }
        });
    }

    public boolean premiumLoginRequirements(String mode) {
        if (Bukkit.getOnlineMode() || !plugin.getConfig().getBoolean("security.auto-login.enabled")) {
            return false;
        }

        if (mode.equalsIgnoreCase("FULL")) {
            if ("PACKETEVENTS".equalsIgnoreCase(plugin.getBackendManager().getActiveBackend())) {
                return plugin.getServer().getPluginManager().getPlugin("packetevents") != null;
            } else {
                return plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
            }
        }

        if (mode.equalsIgnoreCase("MINI")) {
            return plugin.getConfig().getBoolean("security.ip-security.enabled");
        }

        return false;
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (!authenticated) {
            playerStates.put(uuid, new StateEntry(BypassState.DENIED));
            return;
        }

        StateEntry current = playerStates.get(uuid);
        if (current != null && current.state == BypassState.DENIED) {
            return;
        }

        playerStates.put(uuid, new StateEntry(BypassState.AUTHENTICATED));
    }

    public boolean isAuthenticated(UUID uuid) {
        StateEntry entry = playerStates.get(uuid);
        return entry != null && entry.state == BypassState.AUTHENTICATED;
    }

    public void removeAuthenticatedPlayer(UUID uuid) {
        playerStates.remove(uuid);
    }

    public void clearPendingBypass(UUID uuid) {
        playerStates.remove(uuid);
    }
}