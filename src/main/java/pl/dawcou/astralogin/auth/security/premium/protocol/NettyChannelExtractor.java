package pl.dawcou.astralogin.auth.security.premium.protocol;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
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

    /**
     * Wstrzykuje szyfrowanie AES (CipherDecoder i CipherEncoder) do potoku Netty.
     * Metoda jest w 100% niezależna od ProtocolLib/PacketEvents (czyste Netty).
     */
    public void enableEncryptionOnChannel(Channel channel, SecretKey sharedSecret, Runnable afterEncryption) {
        plugin.getPremiumManager().debug("DEBUG [NETTY] 🔧 Preparing Netty channel for encryption injection...");

        if (channel == null) {
            plugin.getPremiumManager().debugSevere("DEBUG [NETTY-ERR] ❌ Netty channel is null!");
            return;
        }

        plugin.getPremiumManager().debug("DEBUG [NETTY] ⚙️ Found Netty channel: " + channel + ", executing encryption pipeline injection on eventLoop...");

        if (!channel.isOpen() || !channel.isActive()) {
            plugin.getPremiumManager().debugSevere(
                    "DEBUG [NETTY-ERR] ❌ Channel is not active: " + channel
            );
            return;
        }

        channel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();

                plugin.getPremiumManager().debug("DEBUG [NETTY] 📋 Current pipeline handlers BEFORE injection: " + pipeline.names());

                if (pipeline.get("decrypt") == null) {
                    MinecraftCipher decryptCipher = new MinecraftCipher(Cipher.DECRYPT_MODE, sharedSecret);
                    pipeline.addBefore("splitter", "decrypt", new CipherDecoder(decryptCipher));
                    plugin.getPremiumManager().debug("DEBUG [NETTY] 🔓 Injected CipherDecoder ('decrypt') before 'splitter'");
                } else {
                    plugin.getPremiumManager().debug("DEBUG [NETTY] ⚠️ 'decrypt' handler already present in pipeline!");
                }

                if (pipeline.get("encrypt") == null) {
                    MinecraftCipher encryptCipher = new MinecraftCipher(Cipher.ENCRYPT_MODE, sharedSecret);

                    if (pipeline.get("prepender") != null) {
                        pipeline.addBefore("prepender", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPremiumManager().debug("DEBUG [NETTY] 🔐 encrypt before prepender");
                    } else if (pipeline.get("encoder") != null) {
                        pipeline.addBefore("encoder", "encrypt", new CipherEncoder(encryptCipher));
                        plugin.getPremiumManager().debug("DEBUG [NETTY] 🔐 encrypt before encoder");
                    } else {
                        plugin.getPremiumManager().debugSevere("DEBUG [NETTY-ERR] ❌ Neither 'prepender' nor 'encoder' exists in pipeline!");
                        return;
                    }
                } else {
                    plugin.getPremiumManager().debug("DEBUG [NETTY] ⚠️ 'encrypt' handler already present in pipeline!");
                }

                plugin.getPremiumManager().debug("DEBUG [NETTY] 📋 Updated pipeline handlers AFTER injection: " + pipeline.names());

                if (afterEncryption != null) {
                    afterEncryption.run();
                }
            } catch (Exception e) {
                plugin.getPremiumManager().debugSevere("DEBUG [NETTY-ERR] ❌ Failed to enable encryption on Netty channel: " + e.getClass().getName() + ": " + e.getMessage());
                if (plugin.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }
}