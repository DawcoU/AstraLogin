package pl.dawcou.astralogin.auth.security.premium.netty;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.crypto.CipherDecoder;
import pl.dawcou.astralogin.auth.security.premium.crypto.CipherEncoder;
import pl.dawcou.astralogin.auth.security.premium.crypto.MinecraftCipher;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class NettyChannelExtractor {

    private final AstraLogin plugin;

    public NettyChannelExtractor(AstraLogin plugin) {
        this.plugin = plugin;
    }

    public void enableEncryptionOnChannel(Channel channel, SecretKey sharedSecret, Runnable afterEncryption) {
        plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 🔧 Preparing Netty channel for encryption injection...");

        if (channel == null) {
            plugin.getPremiumProtocolListener().debugSevere("DEBUG [NETTY-ERR] ❌ Netty channel is null!");
            return;
        }

        plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] ⚙️ Found Netty channel: " + channel + ", executing encryption pipeline injection on eventLoop...");

        if (!channel.isOpen() || !channel.isActive()) {
            plugin.getPremiumProtocolListener().debugSevere(
                    "[NETTY-ERR] ❌ Channel is not active: " + channel
            );
            return;
        }

        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();

                plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 📋 Current pipeline handlers BEFORE injection: " + pipeline.names());

                if (pipeline.get("decrypt") == null) {
                    MinecraftCipher decryptCipher = new MinecraftCipher(Cipher.DECRYPT_MODE, sharedSecret);
                    pipeline.addBefore("splitter", "decrypt", new CipherDecoder(decryptCipher));
                    plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 🔓 Injected CipherDecoder ('decrypt') before 'splitter'");
                } else {
                    plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] ⚠️ 'decrypt' handler already present in pipeline!");
                }

                if (pipeline.get("encrypt") == null) {
                    MinecraftCipher encryptCipher = new MinecraftCipher(Cipher.ENCRYPT_MODE, sharedSecret);

                    if (pipeline.get("prepender") != null) {
                        pipeline.addBefore("prepender", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 🔐 encrypt before prepender");
                    } else if (pipeline.get("encoder") != null) {
                        pipeline.addBefore("encoder", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 🔐 encrypt before encoder");
                    } else {
                        plugin.getPremiumProtocolListener().debugSevere("DEBUG [NETTY-ERR] ❌ Neither 'prepender' nor 'encoder' exists in pipeline!");
                        return;
                    }
                } else {
                    plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] ⚠️ 'encrypt' handler already present in pipeline!");
                }

                plugin.getPremiumProtocolListener().debug("DEBUG [NETTY] 📋 Updated pipeline handlers AFTER injection: " + pipeline.names());

                if (afterEncryption != null) {
                    afterEncryption.run();
                }
            } catch (Exception e) {
                plugin.getPremiumProtocolListener().debugSevere("DEBUG [NETTY-ERR] ❌ Failed to enable encryption on Netty channel: " + e.getClass().getName() + ": " + e.getMessage());
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
                plugin.getPremiumProtocolListener().debugSevere(
                        "[NETTY-EXTRACT] ❌ Injector is null!"
                );
                return null;
            }

            FieldAccessor channelAccessor =
                    Accessors.getFieldAccessor(injector.getClass(), Channel.class, true);

            Channel channel = (Channel) channelAccessor.get(injector);

            if (channel == null) {
                plugin.getPremiumProtocolListener().debugSevere(
                        "[NETTY-EXTRACT] ❌ Channel is null!"
                );
                return null;
            }

            plugin.getPremiumProtocolListener().debug(
                    "[NETTY-EXTRACT] ✅ Channel obtained directly: " + channel
            );

            return channel;

        } catch (Exception e) {
            plugin.getPremiumProtocolListener().debugSevere(
                    "[NETTY-EXTRACT-ERR] ❌ " +
                            e.getClass().getName() + ": " + e.getMessage()
            );

            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }

            return null;
        }
    }
}