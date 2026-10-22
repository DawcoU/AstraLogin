package pl.dawcou.astralogin.auth.security.premium.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
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

    /**
     * Uniwersalna metoda pobierająca profil gracza z Mojang API (lub mocka dev),
     * zwracająca czysty obiekt GameProfile (w pełni niezależny od ProtocolLib).
     */
    public GameProfile fetchMojangProfile(String username, String serverHash) {
        if (plugin.isDevMockMode()) {
            plugin.getPremiumManager().debugWarning("[DEV-MOCK] Bypassing HTTP request to Mojang for testing!");

            UUID mockUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            return new GameProfile(mockUuid, username);
        }

        try {
            String urlString = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&serverId="
                    + URLEncoder.encode(serverHash, StandardCharsets.UTF_8);

            plugin.getPremiumManager().debug("DEBUG [Mojang API] 🌐 Requesting hasJoined: username=" + username + ", serverHash=" + serverHash);
            plugin.getPremiumManager().debug("DEBUG [Mojang API] 🔗 Full URL: " + urlString);

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

                GameProfile profile = new GameProfile(uuid, name);
                JSONArray properties = (JSONArray) response.get("properties");

                if (properties != null) {
                    for (Object obj : properties) {
                        JSONObject prop = (JSONObject) obj;
                        String propName = (String) prop.get("name");
                        String value = (String) prop.get("value");
                        String signature = (String) prop.get("signature");

                        if (propName != null && value != null) {
                            // Dodajemy właściwości (np. textures) bezpośrednio do czystego GameProfile
                            profile.getProperties().put(propName, new Property(propName, value, signature));
                        }
                    }
                }

                plugin.getPremiumManager().debug("DEBUG [MOJANG-API] 🔓 Player " + name + " successfully verified by Mojang!");

                return profile;
            } else {
                plugin.getPremiumManager().debugWarning("DEBUG [Mojang API] ❌ Unexpected HTTP response=" + responseCode + " for " + username);
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