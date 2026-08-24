package pl.dawcou.astralogin.auth.security.premium.netty;

import com.comphenix.protocol.events.PacketEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.crypto.CipherDecoder;
import pl.dawcou.astralogin.auth.security.premium.crypto.CipherEncoder;
import pl.dawcou.astralogin.auth.security.premium.crypto.MinecraftCipher;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;

public class NettyChannelExtractor {

    private final AstraLogin plugin;

    public NettyChannelExtractor(AstraLogin plugin) {
        this.plugin = plugin;
    }

    private void debug(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info(message);
        }
    }

    private void debugSevere(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().severe(message);
        }
    }

    public void enableEncryptionOnChannel(Channel channel, SecretKey sharedSecret, Runnable afterEncryption) {
        debug("DEBUG [NETTY] 🔧 Preparing Netty channel for encryption injection...");

        if (channel == null) {
            debugSevere("DEBUG [NETTY-ERR] ❌ Netty channel is null!");
            return;
        }

        debug("DEBUG [NETTY] ⚙️ Found Netty channel: " + channel + ", executing encryption pipeline injection on eventLoop...");

        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();

                debug("DEBUG [NETTY] 📋 Current pipeline handlers BEFORE injection: " + pipeline.names());

                if (pipeline.get("decrypt") == null) {
                    MinecraftCipher decryptCipher = new MinecraftCipher(Cipher.DECRYPT_MODE, sharedSecret);
                    pipeline.addBefore("splitter", "decrypt", new CipherDecoder(decryptCipher));
                    debug("DEBUG [NETTY] 🔓 Injected CipherDecoder ('decrypt') before 'splitter'");
                } else {
                    debug("DEBUG [NETTY] ⚠️ 'decrypt' handler already present in pipeline!");
                }

                if (pipeline.get("encrypt") == null) {
                    MinecraftCipher encryptCipher = new MinecraftCipher(Cipher.ENCRYPT_MODE, sharedSecret);
                    pipeline.addBefore("encoder", "encrypt", new CipherEncoder(encryptCipher));
                    debug("DEBUG [NETTY] 🔐 Injected CipherEncoder ('encrypt') before 'encoder'");
                } else {
                    debug("DEBUG [NETTY] ⚠️ 'encrypt' handler already present in pipeline!");
                }

                debug("DEBUG [NETTY] 📋 Updated pipeline handlers AFTER injection: " + pipeline.names());

                if (afterEncryption != null) {
                    afterEncryption.run();
                }
            } catch (Exception e) {
                debugSevere("DEBUG [NETTY-ERR] ❌ Failed to enable encryption on Netty channel: " + e.getClass().getName() + ": " + e.getMessage());
                if (plugin.isDebugMode()) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Channel extractChannelFromEvent(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null || player.getAddress() == null) {
                debug("DEBUG [NETTY-EXTRACT] ❌ Player or player address is null!");
                return null;
            }

            InetSocketAddress clientAddress = player.getAddress();
            debug("DEBUG [NETTY-EXTRACT] 🔎 Searching channel for address: " + clientAddress);

            Object minecraftServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object serverConnection = minecraftServer.getClass().getMethod("getConnection").invoke(minecraftServer);

            if (serverConnection == null) {
                debug("DEBUG [NETTY-EXTRACT] ⚠️ getConnection() returned null, trying fallback reflection field 'connection'...");
                Field connField = minecraftServer.getClass().getDeclaredField("connection");
                connField.setAccessible(true);
                serverConnection = connField.get(minecraftServer);
            }

            if (serverConnection == null) {
                debugSevere("DEBUG [NETTY-EXTRACT] ❌ ServerConnection is null!");
                return null;
            }

            for (Field field : serverConnection.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(serverConnection);

                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item == null) continue;
                        Channel channel = findChannelByAddress(item, clientAddress);
                        if (channel != null) {
                            debug("DEBUG [NETTY-EXTRACT] ✅ Channel found via reflection: " + channel);
                            return channel;
                        }
                    }
                }
            }
        } catch (Exception e) {
            debugSevere("DEBUG [NETTY-EXTRACT-ERR] ❌ Error retrieving channel: " + e.getClass().getName() + ": " + e.getMessage());
            if (plugin.isDebugMode()) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private Channel findChannelByAddress(Object networkManagerObj, InetSocketAddress targetAddress) {
        try {
            Class<?> clazz = networkManagerObj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object val = field.get(networkManagerObj);

                    if (val instanceof Channel ch) {
                        if (ch.remoteAddress() != null && ch.remoteAddress().equals(targetAddress)) {
                            return ch;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }
}