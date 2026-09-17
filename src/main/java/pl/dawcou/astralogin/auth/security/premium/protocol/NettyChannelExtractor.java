package pl.dawcou.astralogin.auth.security.premium.protocol;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.CipherDecoder;
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.CipherEncoder;
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.MinecraftCipher;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class NettyChannelExtractor {

    private final AstraLogin plugin;

    public NettyChannelExtractor(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void enableEncryptionOnChannel(Channel channel, SecretKey sharedSecret, Runnable afterEncryption) {
        plugin.getPacketListener().debug("DEBUG [NETTY] 🔧 Preparing Netty channel for encryption injection...");

        if (channel == null) {
            plugin.getPacketListener().debugSevere("DEBUG [NETTY-ERR] ❌ Netty channel is null!");
            return;
        }

        plugin.getPacketListener().debug("DEBUG [NETTY] ⚙️ Found Netty channel: " + channel + ", executing encryption pipeline injection on eventLoop...");

        if (!channel.isOpen() || !channel.isActive()) {
            plugin.getPacketListener().debugSevere(
                    "DEBUG [NETTY-ERR] ❌ Channel is not active: " + channel
            );
            return;
        }

        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();

                plugin.getPacketListener().debug("DEBUG [NETTY] 📋 Current pipeline handlers BEFORE injection: " + pipeline.names());

                if (pipeline.get("decrypt") == null) {
                    MinecraftCipher decryptCipher = new MinecraftCipher(Cipher.DECRYPT_MODE, sharedSecret);
                    pipeline.addBefore("splitter", "decrypt", new CipherDecoder(decryptCipher));
                    plugin.getPacketListener().debug("DEBUG [NETTY] 🔓 Injected CipherDecoder ('decrypt') before 'splitter'");
                } else {
                    plugin.getPacketListener().debug("DEBUG [NETTY] ⚠️ 'decrypt' handler already present in pipeline!");
                }

                if (pipeline.get("encrypt") == null) {
                    MinecraftCipher encryptCipher = new MinecraftCipher(Cipher.ENCRYPT_MODE, sharedSecret);

                    if (pipeline.get("prepender") != null) {
                        pipeline.addBefore("prepender", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPacketListener().debug("DEBUG [NETTY] 🔐 encrypt before prepender");
                    } else if (pipeline.get("encoder") != null) {
                        pipeline.addBefore("encoder", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPacketListener().debug("DEBUG [NETTY] 🔐 encrypt before encoder");
                    } else {
                        plugin.getPacketListener().debugSevere("DEBUG [NETTY-ERR] ❌ Neither 'prepender' nor 'encoder' exists in pipeline!");
                        return;
                    }
                } else {
                    plugin.getPacketListener().debug("DEBUG [NETTY] ⚠️ 'encrypt' handler already present in pipeline!");
                }

                plugin.getPacketListener().debug("DEBUG [NETTY] 📋 Updated pipeline handlers AFTER injection: " + pipeline.names());

                if (afterEncryption != null) {
                    afterEncryption.run();
                }
            } catch (Exception e) {
                plugin.getPacketListener().debugSevere("DEBUG [NETTY-ERR] ❌ Failed to enable encryption on Netty channel: " + e.getClass().getName() + ": " + e.getMessage());
                if (plugin.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Channel extractChannelFromEvent(PacketEvent event) {
        try {
            Player player = event.getPlayer();

            if (player == null) {
                return null;
            }

            Injector injector = TemporaryPlayerFactory.getInjectorFromPlayer(player);

            if (injector == null) {
                plugin.getPacketListener().debugSevere(
                        "DEBUG [NETTY-EXTRACT] ❌ Injector is null!"
                );
                return null;
            }

            FieldAccessor channelAccessor =
                    Accessors.getFieldAccessor(injector.getClass(), Channel.class, true);

            Channel channel = (Channel) channelAccessor.get(injector);

            if (channel == null) {
                plugin.getPacketListener().debugSevere(
                        "DEBUG [NETTY-EXTRACT] ❌ Channel is null!"
                );
                return null;
            }

            plugin.getPacketListener().debug(
                    "DEBUG [NETTY-EXTRACT] ✅ Channel obtained directly: " + channel
            );

            return channel;

        } catch (Exception e) {
            plugin.getPacketListener().debugSevere(
                    "DEBUG [NETTY-EXTRACT-ERR] ❌ " +
                            e.getClass().getName() + ": " + e.getMessage()
            );

            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }

            return null;
        }
    }
}