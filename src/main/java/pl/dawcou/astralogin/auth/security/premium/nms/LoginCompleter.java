package pl.dawcou.astralogin.auth.security.premium.nms;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import pl.dawcou.astralogin.AstraLogin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.UUID;

public class LoginCompleter {

    private final AstraLogin plugin;
    private static MethodHandle startClientVerificationHandle;

    static {
        try {
            Method method = ServerLoginPacketListenerImpl.class.getDeclaredMethod("startClientVerification", GameProfile.class);
            method.setAccessible(true);
            startClientVerificationHandle = MethodHandles.lookup().unreflect(method);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public LoginCompleter(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void complete(Channel channel, WrappedGameProfile mojangProfile) {
        try {
            Object packetHandler = channel.pipeline().get("packet_handler");

            if (!(packetHandler instanceof Connection connection)) {
                plugin.getPremiumProtocolListener().debug("[LoginCompleter] Netty handler 'packet_handler' is not an instance of Connection!");
                return;
            }

            PacketListener packetListener = connection.getPacketListener();

            if (!(packetListener instanceof ServerLoginPacketListenerImpl loginListener)) {
                plugin.getPremiumProtocolListener().debugSevere("[LoginCompleter] PacketListener is not ServerLoginPacketListenerImpl!");
                return;
            }

            GameProfile nmsProfile = toNmsProfile(mojangProfile);

            if (startClientVerificationHandle != null) {
                startClientVerificationHandle.invoke(loginListener, nmsProfile);
            } else {
                plugin.getPremiumProtocolListener().debugSevere("[LoginCompleter] MethodHandle for startClientVerification is not initialized!");
                return;
            }

            // Czyste wyciągnięcie nazwy refleksją z pola 'name' w AuthLib GameProfile
            String playerName = fetchNameReflectively(nmsProfile);

            plugin.getPremiumProtocolListener().debug("[LoginCompleter] Successfully completed Premium authorization for " + playerName);

        } catch (Throwable e) {
            plugin.getPremiumProtocolListener().debugSevere("[LoginCompleter] Error while invoking startClientVerification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private GameProfile toNmsProfile(WrappedGameProfile wrapped) {
        if (wrapped.getHandle() instanceof GameProfile directProfile) {
            return directProfile;
        }

        // Jeśli handle nie jest obiektem GameProfile, tworzymy fallbackowy profil
        return new GameProfile(
                UUID.nameUUIDFromBytes(("OfflinePlayer:Unknown").getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "Unknown"
        );
    }

    private String fetchNameReflectively(GameProfile profile) {
        if (profile == null) return "Unknown";
        try {
            java.lang.reflect.Field nameField = GameProfile.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(profile);
        } catch (Exception e) {
            return "Unknown";
        }
    }
}