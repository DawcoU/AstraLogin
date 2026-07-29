package pl.dawcou.astralogin.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

    public PremiumManager(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Boolean> getPremiumCache() {
        return Collections.unmodifiableMap(premiumCache);
    }

    public boolean isPremium(Player p) {
        UUID uuid = p.getUniqueId();

        if (Bukkit.getOnlineMode() || !plugin.getConfig().getBoolean("features.auto-login.enabled")
                || !plugin.getConfig().getBoolean("security.ip-security.enabled")
                || !plugin.getPasswordManager().isRegistered(uuid.toString())) {

            return false;
        }

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

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + p.getName()))
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
}