package pl.dawcou.astralogin.auth.security.ip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.GlobalDataManager;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//--------------------------------------------------
// Menedżer blokad IP oparty o GlobalDataManager
//--------------------------------------------------
public class IPBanManager {

    private final AstraLogin plugin;
    private final GlobalDataManager globalDataManager;

    private final Map<String, Integer> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBans = new ConcurrentHashMap<>();
    private final Map<String, String> banReasons = new ConcurrentHashMap<>();
    private final Map<String, String> ipBannedUuid = new ConcurrentHashMap<>();

    private final Set<String> ipBypassUuids = ConcurrentHashMap.newKeySet();

    public IPBanManager(AstraLogin plugin, GlobalDataManager globalDataManager) {
        this.plugin = plugin;
        this.globalDataManager = globalDataManager;
        loadBans();
    }

    public boolean isIPBanned(String ip) {
        if (!ipBans.containsKey(ip)) return false;
        if (System.currentTimeMillis() > ipBans.get(ip)) {
            unbanIP(ip);
            return false;
        }
        return true;
    }

    public String getBanReason(String ip) {
        return banReasons.getOrDefault(ip, "UNKNOWN");
    }

    public void banIPWithMillis(String ip, long durationMillis, String reason, String uuid) {
        ipBans.put(ip, System.currentTimeMillis() + durationMillis);
        banReasons.put(ip, reason);
        if (uuid != null) {
            ipBannedUuid.put(ip, uuid);
        }
        saveBans();
    }

    public void unbanIP(String ip) {
        ipAttempts.remove(ip);
        ipBans.remove(ip);
        banReasons.remove(ip);
        ipBannedUuid.remove(ip);
        saveBans();
    }

    public void addIPAttempt(String ip, String uuid) {
        String path = "security.anti-spam.";

        int max = plugin.getConfig().getInt(path + "max-attempts", 5);
        String timeStr = plugin.getConfig().getString(path + "tempban-time", "30 minutes");

        int current = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, current);

        if (current >= max) {
            long banMillis = TimeUtils.parseTime(timeStr, 600000L);
            banIPWithMillis(ip, banMillis, "SPAM", uuid);
            ipAttempts.remove(ip);
            plugin.getIpTrustManager().addTrustScore(
                    ip,
                    plugin.getIpTrustManager().getIpSpamPoints()
            );
        }
    }

    public long getIPBanTimeLeft(String ip) {
        if (!ipBans.containsKey(ip)) return 0;
        long timeLeft = (ipBans.get(ip) - System.currentTimeMillis()) / 1000;
        return Math.max(0, timeLeft);
    }

    public void resetIPAttempts(String ip) {
        unbanIP(ip);
    }

    public boolean hasBypass(String uuid) {
        return ipBypassUuids.contains(uuid);
    }

    public void setBypass(String uuid, boolean status) {
        if (status) {
            ipBypassUuids.add(uuid);
        } else {
            ipBypassUuids.remove(uuid);
        }
        saveBans();
    }

    public void saveBans() {
        JsonObject bansJson = new JsonObject();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : ipBans.entrySet()) {
            String ip = entry.getKey();
            long expireTime = entry.getValue();

            if (expireTime > now) {
                JsonObject banData = new JsonObject();
                banData.addProperty("ip", ip);
                banData.addProperty("expire", expireTime);
                banData.addProperty("reason", banReasons.getOrDefault(ip, "UNKNOWN"));
                banData.addProperty("uuid", ipBannedUuid.getOrDefault(ip, "UNKNOWN"));

                bansJson.add(ip, banData);
            }
        }

        JsonArray bypassesArray = new JsonArray();
        for (String uuid : ipBypassUuids) {
            bypassesArray.add(uuid);
        }

        // Zapisujemy cały obiekt banów i tablicę bypassów przez setExplicit
        globalDataManager.setExplicit(bansJson, "ip_bans");
        globalDataManager.setExplicit(bypassesArray, "ip_bypasses");
    }

    public void loadBans() {
        ipBans.clear();
        banReasons.clear();
        ipBannedUuid.clear();
        ipBypassUuids.clear();

        // Pobieramy JsonElement za pomocą getElementExplicit, żeby ominąć kropki
        JsonElement bansElement = globalDataManager.getElementExplicit("ip_bans");
        if (bansElement != null && bansElement.isJsonObject()) {
            JsonObject bansJson = bansElement.getAsJsonObject();
            long now = System.currentTimeMillis();

            for (Map.Entry<String, JsonElement> entry : bansJson.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject banData = entry.getValue().getAsJsonObject();
                    String ip = banData.has("ip") ? banData.get("ip").getAsString() : entry.getKey();
                    long expire = banData.has("expire") ? banData.get("expire").getAsLong() : 0;
                    String reason = banData.has("reason") ? banData.get("reason").getAsString() : "UNKNOWN";
                    String uuid = banData.has("uuid") ? banData.get("uuid").getAsString() : "UNKNOWN";

                    if (ip != null && expire > now) {
                        ipBans.put(ip, expire);
                        banReasons.put(ip, reason);
                        ipBannedUuid.put(ip, uuid);
                    }
                }
            }
        }

        // Pobieramy bezpośrednio JsonElement, sprawdzamy czy to JsonArray
        JsonElement bypassesElement = globalDataManager.getElementExplicit("ip_bypasses");
        if (bypassesElement != null && bypassesElement.isJsonArray()) {
            for (JsonElement element : bypassesElement.getAsJsonArray()) {
                ipBypassUuids.add(element.getAsString());
            }
        }
    }
}