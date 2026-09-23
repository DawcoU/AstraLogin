package pl.dawcou.astralogin.auth.security.ip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.data.GlobalDataManager;
import pl.dawcou.astralogin.data.PlayerDataManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class IPManager {

    // ----------------------------------------------------------------------------------------------------
    // Fields & Dependencies
    // ----------------------------------------------------------------------------------------------------
    private final AstraLogin plugin;
    private final PlayerDataManager playerDataManager;
    private final GlobalDataManager globalDataManager;
    private final IPBanManager ipBanManager;

    private int ipv4CheckOctets = 2;
    private int ipv6CheckBlocks = 4;

    // ----------------------------------------------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------------------------------------------
    public IPManager(AstraLogin plugin, PlayerDataManager playerDataManager, GlobalDataManager globalDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.globalDataManager = globalDataManager;
        this.ipBanManager = new IPBanManager(plugin, globalDataManager);

        reload();
    }

    public IPBanManager getIpBanManager() {
        return ipBanManager;
    }

    // ----------------------------------------------------------------------------------------------------
    // IP Verification Methods
    // ----------------------------------------------------------------------------------------------------
    public boolean checkIP(String uuid, String savedIP, String currentIP) {
        if (uuid != null && ipBanManager.hasBypass(uuid)) {
            return true;
        }
        return isValidIP(savedIP, currentIP);
    }

    public boolean isValidIP(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equalsIgnoreCase(currentIP)) return true;

        try {
            InetAddress savedAddr = InetAddress.getByName(savedIP);
            InetAddress currentAddr = InetAddress.getByName(currentIP);

            byte[] savedBytes = savedAddr.getAddress();
            byte[] currentBytes = currentAddr.getAddress();

            // Return false if protocol versions differ (e.g. IPv4 vs IPv6)
            if (savedBytes.length != currentBytes.length) {
                return false;
            }

            // IPv4 handling (4 bytes)
            if (savedBytes.length == 4) {
                for (int i = 0; i < ipv4CheckOctets; i++) {
                    if (savedBytes[i] != currentBytes[i]) {
                        return false;
                    }
                }
                return true;
            }

            // IPv6 handling (16 bytes = 8 blocks of 2 bytes each)
            if (savedBytes.length == 16) {
                int bytesToCheck = ipv6CheckBlocks * 2;
                for (int i = 0; i < bytesToCheck; i++) {
                    if (savedBytes[i] != currentBytes[i]) {
                        return false;
                    }
                }
                return true;
            }

        } catch (UnknownHostException e) {
            // Fallback for invalid IP strings
            return savedIP.equalsIgnoreCase(currentIP);
        }

        return false;
    }

    // ----------------------------------------------------------------------------------------------------
    // Player Data IP Operations
    // ----------------------------------------------------------------------------------------------------
    public void saveIP(String uuid, String ip) {
        try {
            UUID parsedUuid = UUID.fromString(uuid);
            playerDataManager.set(parsedUuid, "auth.last-ip", ip);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not parse UUID for IP saving: " + uuid);
        }
    }

    public String getIP(String uuid) {
        try {
            UUID parsedUuid = UUID.fromString(uuid);
            return playerDataManager.getString(parsedUuid, "auth.last-ip");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not parse UUID for IP retrieval: " + uuid);
            return null;
        }
    }

    public void deleteIP(String uuid) {
        try {
            UUID parsedUuid = UUID.fromString(uuid);
            playerDataManager.remove(parsedUuid, "auth.last-ip");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not parse UUID for IP deletion: " + uuid);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Utility & Mapping Methods
    // ----------------------------------------------------------------------------------------------------
    public Map<String, List<String>> getIpToNamesMap() {
        Map<String, List<String>> ipToNamesMap = new HashMap<>();
        JsonObject uuidsObj = globalDataManager.getJsonObject("usermap.uuids");

        if (uuidsObj != null) {
            for (Map.Entry<String, JsonElement> entry : uuidsObj.entrySet()) {
                String name = entry.getKey();
                try {
                    UUID uuid = UUID.fromString(entry.getValue().getAsString());
                    String ip = playerDataManager.getString(uuid, "auth.last-ip");

                    if (ip != null && !ip.isEmpty()) {
                        ipToNamesMap.computeIfAbsent(ip, k -> new ArrayList<>()).add(name);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return ipToNamesMap;
    }

    public int getNumberOfAccountsByIP(String ip) {
        if (ip == null || ip.isEmpty()) return 0;
        List<String> names = getIpToNamesMap().get(ip);
        return names != null ? names.size() : 0;
    }

    // ----------------------------------------------------------------------------------------------------
    // IP Ban Manager Wrappers
    // ----------------------------------------------------------------------------------------------------
    public boolean isIPBanned(String ip) {
        return ipBanManager.isIPBanned(ip);
    }

    public String getBanReason(String ip) {
        return ipBanManager.getBanReason(ip);
    }

    public void banIPWithMillis(String ip, long durationMillis, String reason) {
        ipBanManager.banIPWithMillis(ip, durationMillis, reason, null);
    }

    public void addIPAttempt(String ip) {
        ipBanManager.addIPAttempt(ip, null);
    }

    public long getIPBanTimeLeft(String ip) {
        return ipBanManager.getIPBanTimeLeft(ip);
    }

    public void resetIPAttempts(String ip) {
        ipBanManager.resetIPAttempts(ip);
    }

    // ----------------------------------------------------------------------------------------------------
    // Configuration Reload
    // ----------------------------------------------------------------------------------------------------
    public void reload() {
        int v4Octets = plugin.getConfig().getInt("ip-security.verification.ipv4-check-octets", 2);
        this.ipv4CheckOctets = Math.max(1, Math.min(4, v4Octets));

        int v6Blocks = plugin.getConfig().getInt("ip-security.verification.ipv6-check-blocks", 4);
        this.ipv6CheckBlocks = Math.max(1, Math.min(8, v6Blocks));

        ipBanManager.loadBans();
    }
}