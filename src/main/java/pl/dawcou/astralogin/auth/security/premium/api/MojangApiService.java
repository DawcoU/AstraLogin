package pl.dawcou.astralogin.auth.security.premium.api;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.protocol.reflections.ProfilesReflection;

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

            UUID mockUuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + username)
                            .getBytes(StandardCharsets.UTF_8)
            );

            GameProfile profile = new GameProfile(mockUuid, username);

            String testValue = "astralogin-reflection-test";
            String testSignature = "astralogin-mock-signature";

            profile = ProfilesReflection.withProfileProperty(
                    profile,
                    "astralogin_test",
                    testValue,
                    testSignature
            );

            if (profile == null) {
                plugin.getLogger().severe(
                        "[DEV-MOCK] FAIL: Could not create profile with property!"
                );
                return null;
            }

            boolean readable = ProfilesReflection.hasProfileProperty(
                    profile,
                    "astralogin_test",
                    testValue
            );

            plugin.getPremiumManager().debug(
                    "[DEV-MOCK] Property read-back: " + readable
            );

            if (!readable) {
                plugin.getLogger().severe(
                        "[DEV-MOCK] FAIL: Property could not be read back!"
                );
                return null;
            }

            plugin.getPremiumManager().debug(
                    "[DEV-MOCK] PASS: Profile property added and read successfully."
            );

            return profile;
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
                try (InputStream inputStream = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(
                             inputStream, StandardCharsets.UTF_8)) {

                    JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();

                    String idStr = response.has("id") && !response.get("id").isJsonNull()
                            ? response.get("id").getAsString() : null;

                    String name = response.has("name") && !response.get("name").isJsonNull()
                            ? response.get("name").getAsString() : null;

                    if (idStr == null || name == null) {
                        return null;
                    }

                    UUID uuid = UUID.fromString(idStr.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                            "$1-$2-$3-$4-$5"
                    ));

                    GameProfile profile = new GameProfile(uuid, name);
                    JsonArray properties = response.has("properties")
                            && response.get("properties").isJsonArray()
                            ? response.getAsJsonArray("properties") : null;

                    if (properties != null) {
                        for (JsonElement element : properties) {
                            if (!element.isJsonObject()) {
                                continue;
                            }

                            JsonObject prop = element.getAsJsonObject();

                            String propName = prop.has("name") && !prop.get("name").isJsonNull()
                                    ? prop.get("name").getAsString() : null;

                            String value = prop.has("value") && !prop.get("value").isJsonNull()
                                    ? prop.get("value").getAsString() : null;

                            String signature = prop.has("signature")
                                    && !prop.get("signature").isJsonNull()
                                    ? prop.get("signature").getAsString() : null;

                            if (propName != null && value != null) {
                                GameProfile updatedProfile =
                                        ProfilesReflection.withProfileProperty(
                                                profile,
                                                propName,
                                                value,
                                                signature
                                        );

                                if (updatedProfile == null) {
                                    plugin.getLogger().severe(
                                            "[MOJANG-API-ERR] Failed to add profile property: "
                                                    + propName
                                    );
                                    return null;
                                }

                                profile = updatedProfile;
                            }
                        }
                    }

                    plugin.getPremiumManager().debug(
                            "DEBUG [MOJANG-API] 🔓 Player " + name
                                    + " successfully verified by Mojang!"
                    );

                    return profile;
                }
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