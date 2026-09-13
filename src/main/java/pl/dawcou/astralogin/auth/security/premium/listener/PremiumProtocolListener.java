package pl.dawcou.astralogin.auth.security.premium.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.api.MojangApiService;
import pl.dawcou.astralogin.auth.security.premium.crypto.EncryptionUtil;
import pl.dawcou.astralogin.auth.security.premium.netty.NettyChannelExtractor;
import pl.dawcou.astralogin.auth.security.premium.nms.LoginCompleter;
import pl.dawcou.astralogin.auth.security.premium.session.PremiumSessionManager;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PremiumProtocolListener implements Listener {

    private final AstraLogin plugin;
    private final ProtocolManager protocolManager;
    private final KeyPair keyPair;

    private final MojangApiService mojangApi;
    private final NettyChannelExtractor nettyExtractor;
    private final PremiumSessionManager sessionManager;

    public PremiumProtocolListener(AstraLogin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.keyPair = plugin.getKeyPair();

        this.mojangApi = new MojangApiService(plugin);
        this.nettyExtractor = new NettyChannelExtractor(plugin);
        this.sessionManager = new PremiumSessionManager();

        registerListeners();
    }

    public void debug(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().info(message);
        }
    }

    public void debugWarning(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().warning(message);
        }
    }

    public void debugSevere(String message) {
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().severe(message);
        }
    }

    private void registerListeners() {
        // --- 1. ODBIERANIE PACKET START & WYSYŁANIE ENCRYPTION REQUEST ---
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                debug("START packet received");

                boolean premiumAllowed = PremiumProtocolListener.this.plugin.getPremiumManager().premiumLoginRequirements("FULL");
                if (!premiumAllowed) return;

                Player player = event.getPlayer();
                String username;

                try {
                    username = event.getPacket().getStrings().read(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                // Wyciągamy adres i Channel na głównym wątku
                Channel channel = nettyExtractor.extractChannelFromEvent(event);

                String offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();

                if (!PremiumProtocolListener.this.plugin.getPasswordManager().isRegistered(offlineUuid)) {
                    debugWarning("Player " + username+ " is not registered");
                    return;
                }

                event.setCancelled(true);
                final PacketContainer startPacket = event.getPacket();

                PremiumProtocolListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    boolean isAccountPremium = PremiumProtocolListener.this.plugin.getPremiumManager().isUsernamePremium(username);

                    if (!isAccountPremium) {
                        try {
                            protocolManager.receiveClientPacket(player, startPacket, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    PremiumProtocolListener.this.plugin.getSchedulerManager().runSync(() -> {
                        if (channel != null) {
                            debug("[NETTY] BEFORE ENCRYPTION_BEGIN pipeline=" + channel.pipeline().names());
                        } else {
                            debugSevere("[NETTY] Could not extract channel for " + username);
                            return;
                        }

                        sessionManager.createSession(channel, username);
                        byte[] verifyToken = sessionManager.getVerifyToken(channel);

                        PacketContainer encryptionRequest = protocolManager.createPacket(PacketType.Login.Server.ENCRYPTION_BEGIN);

                        try {
                            // 1. Server ID – zawsze pusty string
                            encryptionRequest.getStrings().write(0, "");

                            // 2. Public Key – zawsze raw bajty (najbezpieczniejsze z Via + PacketEvents)
                            byte[] pubKeyBytes = keyPair.getPublic().getEncoded();
                            encryptionRequest.getByteArrays().write(0, pubKeyBytes);

                            // 3. Verify Token
                            encryptionRequest.getByteArrays().write(1, verifyToken);

                            // 4. ShouldAuthenticate
                            if (encryptionRequest.getBooleans().size() > 0) {
                                encryptionRequest.getBooleans().write(0, true);
                                debug("[ENCRYPTION-REQUEST] shouldAuthenticate=true");
                            }

                            // === DEBUG ===
                            debug("[KEY-DEBUG] Our public key length: " + pubKeyBytes.length);
                            try {
                                MessageDigest md = MessageDigest.getInstance("SHA-1");
                                debug("[KEY-DEBUG] Our public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(pubKeyBytes))));

                                byte[] packetKey = encryptionRequest.getByteArrays().read(0);
                                debug("[KEY-DEBUG] Packet public key length: " + packetKey.length);
                                debug("[KEY-DEBUG] Packet public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(packetKey))));
                                debug("[KEY-DEBUG] Keys match: " + java.util.Arrays.equals(pubKeyBytes, packetKey));
                            } catch (Exception ex) {
                                debugWarning("[KEY-DEBUG] Failed to verify key: " + ex.getMessage());
                            }
                            // === KONIEC DEBUG ===

                            protocolManager.sendServerPacket(player, encryptionRequest);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            }
        });

        // --- 2. ODBIERANIE ODPOWIEDZI SZYFROWANIA OD KLIENTA ---
        protocolManager.addPacketListener(new PacketAdapter(
                PacketAdapter.params()
                        .plugin(plugin)
                        .types(PacketType.Login.Client.ENCRYPTION_BEGIN)
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                debug("DEBUG [4.0/6] Received ENCRYPTION_BEGIN packet from client.");

                Player player = event.getPlayer();

                if (player == null) {
                    debugWarning("DEBUG [4.1/6] ❌ Player is null!");
                    return;
                }

                // Pobieramy Channel od razu
                Channel channel = nettyExtractor.extractChannelFromEvent(event);

                if (channel == null) {
                    debugSevere("DEBUG [ERR] ❌ Could not extract Netty channel");
                    return;
                }

                // Channel jest teraz identyfikatorem sesji
                InetSocketAddress socketAddress = player.getAddress();

                String clientAddress = socketAddress != null
                        && socketAddress.getAddress() != null
                        ? socketAddress.getAddress().getHostAddress()
                        : "UNKNOWN";

                String username = sessionManager.getUsername(channel);
                byte[] expectedToken = sessionManager.getVerifyToken(channel);

                debug("DEBUG [4.2/6] 🔎 Session lookup: " +
                        "channel=" + channel +
                        ", pendingUsername=" + username +
                        ", tokenPresent=" + (expectedToken != null));

                if (username == null || expectedToken == null) {
                    debug("DEBUG [4.3/6] ⏭️ No premium session for channel - bypassing.");
                    return;
                }

                // Anulujemy pakiet natychmiast
                event.setCancelled(true);

                // Pobieramy dane pakietu
                PacketContainer packet = event.getPacket();

                byte[] encryptedSecret;
                byte[] encryptedToken;

                try {
                    List<byte[]> byteArrays = packet.getByteArrays().getValues();

                    if (byteArrays.size() >= 2) {
                        encryptedSecret = byteArrays.get(0);
                        encryptedToken = byteArrays.get(1);
                    } else {
                        encryptedSecret = packet.getSpecificModifier(byte[].class).read(0);
                        encryptedToken = packet.getSpecificModifier(byte[].class).read(1);
                    }
                } catch (Exception e) {
                    sessionManager.removeSession(channel);

                    debugSevere(
                            "DEBUG [ERR] ❌ Failed to read byte arrays from packet: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );

                    e.printStackTrace();
                    return;
                }

                if (encryptedSecret == null || encryptedToken == null) {
                    sessionManager.removeSession(channel);
                    debugWarning("DEBUG [ERR] ❌ Null encryption data received from " + clientAddress);
                    return;
                }

                final byte[] finalEncryptedSecret = encryptedSecret;
                final byte[] finalEncryptedToken = encryptedToken;

                // Przechodzimy do wykonywania zadań asynchronicznych z pobranymi danymi
                PremiumProtocolListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    debug("DEBUG [5.0/6] 🔓 Starting RSA decryption for " + username);

                    SecretKey sharedSecret;
                    byte[] decryptedToken;

                    try {
                        sharedSecret = EncryptionUtil.decryptSharedSecret(keyPair.getPrivate(), finalEncryptedSecret);
                        decryptedToken = EncryptionUtil.decrypt(keyPair.getPrivate(), finalEncryptedToken);
                    } catch (Exception e) {
                        sessionManager.removeSession(channel);
                        debugSevere("DEBUG [ERR] ❌ Exception during RSA decryption: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        channel.close();
                        return;
                    }

                    if (sharedSecret == null || decryptedToken == null) {
                        sessionManager.removeSession(channel);
                        debugWarning("DEBUG [ERR] ❌ Failed to decrypt keys for " + clientAddress);
                        channel.close();
                        return;
                    }

                    boolean tokenMatches = Arrays.equals(expectedToken, decryptedToken);

                    if (!tokenMatches) {
                        sessionManager.removeSession(channel);
                        debugWarning("DEBUG [ERR] ❌ Verify token mismatch for " + clientAddress + "!");
                        channel.close();
                        return;
                    }

                    sessionManager.removeSession(channel);

                    debug("DEBUG [KEYS-CHECK] 🔑 SharedSecret raw bytes length: " + sharedSecret.getEncoded().length);
                    debug("DEBUG [KEYS-CHECK] 🔑 PublicKey raw bytes length: " + (keyPair.getPublic() != null ? keyPair.getPublic().getEncoded().length : "null"));

                    try {
                        String serverId = "";
                        String serverHash = EncryptionUtil.generateServerHash(serverId, keyPair.getPublic(), sharedSecret);

                        debug("[HASH-CHECK] serverId='" + serverId + "'");
                        debug("[HASH-CHECK] sharedSecretLength=" + sharedSecret.getEncoded().length);
                        debug("[HASH-CHECK] publicKeyLength=" + keyPair.getPublic().getEncoded().length);
                        debug("[HASH-CHECK] serverHash=" + serverHash);

                        debug("DEBUG [5.7/6] 🌐 Querying Mojang hasJoined API for " + username);

                        debug("DEBUG [HASH-CHECK] ServerId=" + serverId + " | Hash=" + serverHash);

                        WrappedGameProfile mojangProfile = mojangApi.fetchMojangProfile(username, serverHash);

                        if (mojangProfile == null) {
                            debugWarning("DEBUG [NON-PREMIUM] ⚠️ No Mojang session found for " + username + ". Encrypting connection and falling back to offline login.");

                            sessionManager.removeSession(channel);

                            // Generate standard offline profile (v3 UUID)
                            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            GameProfile offlineNmsProfile = new GameProfile(offlineUuid, username);
                            WrappedGameProfile offlineWrappedProfile = WrappedGameProfile.fromHandle(offlineNmsProfile);

                            // Mark as non-premium so AstraLogin forces password check
                            PremiumProtocolListener.this.plugin.getPremiumManager().setAuthenticated(offlineUuid, false);

                            // CRITICAL: We MUST enable AES encryption on Netty pipeline, because client already enabled it!
                            nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                                new LoginCompleter(PremiumProtocolListener.this.plugin)
                                        .complete(channel, offlineWrappedProfile);

                                debug("DEBUG [NON-PREMIUM] 🔓 Encryption injected and offline login completed for " + username);
                            });

                            return;
                        }

                        GameProfile nmsHandle = (GameProfile) mojangProfile.getHandle();
                        UUID uuid = null;

                        try {
                            java.lang.reflect.Field idField = GameProfile.class.getDeclaredField("id");
                            idField.setAccessible(true);
                            uuid = (UUID) idField.get(nmsHandle);
                        } catch (Exception e) {
                            // Fallback gdyby pole nazywało się inaczej
                            plugin.getLogger().severe("Could not extract UUID from GameProfile: " + e.getMessage());
                        }

                        if (uuid != null) {
                            PremiumProtocolListener.this.plugin.getPremiumManager().setAuthenticated(uuid, true);
                        }

                        // Wstrzykujemy szyfrowanie bezpośrednio do obiektu Channel
                        nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                            // Oddajemy flow Paper'owi
                            new LoginCompleter(PremiumProtocolListener.this.plugin)
                                    .complete(channel, mojangProfile);

                            debug("DEBUG [SUCCESS] 🔓 Player " + username + " successfully verified by Mojang!");
                        });

                    } catch (Exception e) {
                        debugSevere("DEBUG [ERR] ❌ Error during Mojang premium verification: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        if (channel.isOpen()) {
                            channel.close();
                        }
                    }
                });
            }
        });
    }
}