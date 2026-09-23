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
        ALLOWED,       // Ustalono na START: Gracz zarejestrowany, czeka na weryfikację pakietową Mojang
        DENIED,        // Ustalono na START: Brak zarejestrowania lub wymuszone hasło (OSTATECZNE - NIE DO NADPISANIA)
        AUTHENTICATED  // Ustalono na SUCCESS: Przedł pomyślnie szyfrowanie Mojang i ma pełny bypass
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
    private final Map<UUID, Boolean> premiumCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> premiumCacheTime = new ConcurrentHashMap<>();

    // Jedna główna mapa zarządzająca stanem gracza
    private final Map<UUID, StateEntry> playerStates = new ConcurrentHashMap<>();

    public Map<UUID, Boolean> getPremiumCache() {
        return Collections.unmodifiableMap(premiumCache);
    }

    public PremiumManager(AstraLogin plugin) {
        this.plugin = plugin;
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

        if ("FULL".equalsIgnoreCase(mode)) {
            return isAuthenticated(uuid);
        }

        Boolean cached = checkCache(uuid);
        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + p.getName()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AstraLogin/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            boolean premium = response.statusCode() == 200;

            premiumCache.put(uuid, premium);
            premiumCacheTime.put(uuid, System.currentTimeMillis());

            return premium;

        } catch (Exception e) {
            plugin.getNoticeManager().sendPremiumCheckError(p.getName(), e);
            return false;
        }
    }

    public boolean isUsernamePremium(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AstraLogin/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            int statusCode = response.statusCode();

            if (plugin.isDebugEnabled()) {
                plugin.getLogger().info("HTTP status for " + username + " = " + statusCode);
            }

            return statusCode == 200;
        } catch (Exception e) {
            plugin.getNoticeManager().sendPremiumCheckError(username, e);
            return false;
        }
    }

    public Boolean checkCache(UUID uuid) {
        Boolean cached = premiumCache.get(uuid);
        Long cacheTime = premiumCacheTime.get(uuid);

        if (cached != null && cacheTime != null) {
            long cacheAge = System.currentTimeMillis() - cacheTime;

            if (cacheAge < 12 * 60 * 60 * 1000) {
                return cached;
            }

            premiumCache.remove(uuid);
            premiumCacheTime.remove(uuid);
        }

        return null;
    }

    public void cleanCache() {
        long now = System.currentTimeMillis();
        long expireTime = 12 * 60 * 60 * 1000;

        premiumCacheTime.forEach((uuid, time) -> {
            if (now - time >= expireTime) {
                premiumCache.remove(uuid);
                premiumCacheTime.remove(uuid);
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
            return plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        }

        if (mode.equalsIgnoreCase("MINI")) {
            return plugin.getConfig().getBoolean("security.ip-security.enabled");
        }

        return false;
    }

    /**
     * Ustawia stan na pakiecie START. Jest to decyzja nadrzędna.
     */
    public void markPendingBypass(UUID uuid, boolean status) {
        playerStates.put(uuid, new StateEntry(status ? BypassState.ALLOWED : BypassState.DENIED));
    }

    /**
     * Zmienia stan na AUTHENTICATED po SUCCESS, ale TYLKO jeśli na START gracz nie dostał DENIED.
     */
    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (!authenticated) {
            playerStates.put(uuid, new StateEntry(BypassState.DENIED));
            return;
        }

        StateEntry current = playerStates.get(uuid);
        // Jeśli na starcie zabroniono bypassu (DENIED), to żaden późniejszy kod tego NIE NADPIŚE
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