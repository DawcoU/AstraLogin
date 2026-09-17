package pl.dawcou.astralogin.auth.security.premium.api;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.mojang.authlib.GameProfile;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import pl.dawcou.astralogin.AstraLogin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MojangApiService {

    private final AstraLogin plugin;

    public MojangApiService(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public WrappedGameProfile fetchMojangProfile(String username, String serverHash) {
        if (plugin.isDevMockMode()) {
            plugin.getLogger().warning("[DEV-MOCK] Bypassing HTTP request to Mojang for testing!");

            UUID mockUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

            // Tworzymy prawdziwy obiekt NMS-owy z Mojang AuthLib
            GameProfile nmsProfile = new GameProfile(mockUuid, username);

            // Opakowujemy go w WrappedGameProfile bezpieczną metodą fromHandle
            return WrappedGameProfile.fromHandle(nmsProfile);
        }

        try {
            String urlString = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&serverId="
                    + URLEncoder.encode(serverHash, StandardCharsets.UTF_8);

            plugin.getPacketListener().debug("DEBUG [Mojang API] 🌐 Requesting hasJoined: username=" + username + ", serverHash=" + serverHash);
            plugin.getPacketListener().debug("DEBUG [Mojang API] 🔗 Full URL: " + urlString);

            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "AstraLogin/" + plugin.getDescription().getVersion());

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                InputStream inputStream = conn.getInputStream();
                JSONParser parser = new JSONParser();
                JSONObject response = (JSONObject) parser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                String idStr = (String) response.get("id");
                String name = (String) response.get("name");

                if (idStr == null || name == null) {
                    return null;
                }

                UUID uuid = UUID.fromString(idStr.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                        "$1-$2-$3-$4-$5"
                ));

                WrappedGameProfile profile = new WrappedGameProfile(uuid, name);
                JSONArray properties = (JSONArray) response.get("properties");

                if (properties != null) {
                    for (Object obj : properties) {
                        JSONObject prop = (JSONObject) obj;
                        String propName = (String) prop.get("name");
                        String value = (String) prop.get("value");
                        String signature = (String) prop.get("signature");

                        if (propName != null && value != null) {
                            profile.getProperties().put(propName, new WrappedSignedProperty(propName, value, signature));
                        }
                    }
                }

                plugin.getPacketListener().debug("DEBUG [MOJANG-API] 🔓 Player " + name + " successfully verified by Mojang!");

                return profile;
            } else {
                plugin.getPacketListener().debugSevere("DEBUG [Mojang API] ❌ Unexpected HTTP response=" + responseCode + " for " + username);
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().severe(
                    "[MOJANG-API-ERR] ❌ Exception during hasJoined: "
                            + e.getClass().getName() + ": " + e.getMessage()
            );
            e.printStackTrace();
            return null;
        }
    }
}