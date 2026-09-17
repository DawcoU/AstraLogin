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

    private final AstraLogin plugin;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<UUID, Boolean> premiumCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> premiumCacheTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> activeSessions = new ConcurrentHashMap<>();

    public Map<UUID, Boolean> getPremiumCache() {
        return Collections.unmodifiableMap(premiumCache);
    }

    public PremiumManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public boolean isPremium(Player p) {
        UUID uuid = p.getUniqueId();

        // 1. Pobieramy wariant z configu
        String mode = plugin.getConfig().getString("security.auto-login.mode", "FULL");

        // 2. Jeśli zwrócą false, natychmiast przerywamy
        if (!premiumLoginRequirements(mode)) {
            return false;
        }

        // 3. Dopiero teraz sprawdzamy resztę warunków (baza danych)
        if (!plugin.getPasswordManager().isRegistered(uuid.toString())) {
            return false;
        }

        // 4. Obsługa trybu FULL
        if ("FULL".equalsIgnoreCase(mode)) {
            return isAuthenticated(uuid);
        }

        // 5. Obsługa trybu MINI (Cache + HTTP Mojang API)
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
            plugin.getNoticeManager().sendPremiumFastCheckError(username, e);
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
    }

    // Weryfikacja wymagań dla logowania Premium (FULL / MINI)
    public boolean premiumLoginRequirements(String mode) {
        // Podstawowe warunki odrzucane na starcie
        if (Bukkit.getOnlineMode() || !plugin.getConfig().getBoolean("security.auto-login.enabled")) {
            return false;
        }

        // Sprawdzanie konkretnego trybu
        if (mode.equalsIgnoreCase("FULL")) {
            return plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        }

        if (mode.equalsIgnoreCase("MINI")) {
            return plugin.getConfig().getBoolean("security.ip-security.enabled");
        }

        return false;
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (authenticated) {
            activeSessions.put(uuid, true);
        } else {
            activeSessions.remove(uuid);
        }
    }

    public boolean isAuthenticated(UUID uuid) {
        return activeSessions.getOrDefault(uuid, false);
    }

    public void removeAuthenticatedPlayer(UUID uuid) {
        activeSessions.remove(uuid);
    }
}