package pl.dawcou.astralogin.auth.security.premium;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    public PremiumManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Boolean> getPremiumCache() {
        return Collections.unmodifiableMap(premiumCache);
    }

    public boolean isPremium(Player p) {
        UUID uuid = p.getUniqueId();

        boolean premiumAllowed = premiumLoginRequirements();

        if (!premiumAllowed || !plugin.getPasswordManager().isRegistered(uuid.toString())) {
            return false;
        }

        // 2. Pobieramy wariant z configu (domyślnie NATIVE)
        String mode = plugin.getConfig().getString("features.auto-login.mode", "NATIVE");

        // 3. Jeśli wybrano nowy system logowania Premium
        if ("FULL".equalsIgnoreCase(mode) && (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null)) {
            return isAuthenticated(uuid);
        }

        // Wymagamy ochronę IP do trybu NATIVE
        if (!plugin.getConfig().getBoolean("security.ip-security.enabled")) {
            return false;
        }

        // 4. TRYB NATIVE (Stare, dobre HTTP Cache + Mojang API)
        Boolean cached = checkCache(uuid);

        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + p.getName()))
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
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            int statusCode = response.statusCode();

            if (plugin.debugMode) {
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

    public boolean premiumLoginRequirements() {
        // Podstawowe warunki blokujące
        return !Bukkit.getOnlineMode() && plugin.getConfig().getBoolean("features.auto-login.enabled");
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        activeSessions.put(uuid, authenticated);
    }

    public boolean isAuthenticated(UUID uuid) {
        return activeSessions.getOrDefault(uuid, false);
    }

    public void removeAuthenticatedPlayer(UUID uuid) {
        activeSessions.remove(uuid);
    }
}