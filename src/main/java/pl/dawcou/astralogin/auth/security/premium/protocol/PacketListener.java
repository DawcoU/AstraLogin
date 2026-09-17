package pl.dawcou.astralogin.auth.security.premium.protocol;

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
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.EncryptionUtils;
import pl.dawcou.astralogin.auth.security.premium.session.PremiumSessionManager;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PacketListener implements Listener {

    private final AstraLogin plugin;
    private final ProtocolManager protocolManager;
    private final KeyPair keyPair;

    private final MojangApiService mojangApi;
    private final NettyChannelExtractor nettyExtractor;
    private final PremiumSessionManager sessionManager;

    public PacketListener(AstraLogin plugin, ProtocolManager protocolManager) {
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
                debug("DEBUG [1.0] 📥 Received START packet.");

                boolean premiumAllowed = PacketListener.this.plugin.getPremiumManager().premiumLoginRequirements("FULL");
                if (!premiumAllowed) {
                    debug("DEBUG [1.1] ⏭️ Premium login is not allowed.");
                    return;
                }

                Player player = event.getPlayer();
                String username;

                try {
                    username = event.getPacket().getStrings().read(0);
                } catch (Exception e) {
                    try {
                        WrappedGameProfile profile = event.getPacket().getGameProfiles().readSafely(0);
                        if (profile == null || profile.getName() == null) return;
                        username = profile.getName();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return;
                    }
                }

                final String playerName = username;

                debug("DEBUG [1.2] 👤 Username received: " + username);

                // Wyciągamy adres i Channel na głównym wątku
                Channel channel = nettyExtractor.extractChannelFromEvent(event);

                debug("DEBUG [1.3] 🔌 Netty channel extracted: " + channel);

                String offlineUuid = UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + username)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ).toString();

                if (!PacketListener.this.plugin.getPasswordManager().isRegistered(offlineUuid)) {
                    debugWarning("DEBUG [1.4] ⚠️ Player " + username + " is not registered.");
                    return;
                }

                debug("DEBUG [1.5] 🛑 Cancelling original START packet for premium verification.");

                event.setCancelled(true);
                final PacketContainer startPacket = event.getPacket();

                PacketListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    boolean isAccountPremium = PacketListener.this.plugin.getPremiumManager().isUsernamePremium(playerName);

                    if (!isAccountPremium) {
                        try {
                            protocolManager.receiveClientPacket(player, startPacket, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    PacketListener.this.plugin.getSchedulerManager().runSync(() -> {
                        if (channel != null) {
                            debug("DEBUG [2.0] 🔌 BEFORE ENCRYPTION_BEGIN pipeline=" + channel.pipeline().names());
                        } else {
                            debugSevere("DEBUG [2.0.ERR] ❌ Could not extract channel for " + playerName);
                            return;
                        }

                        sessionManager.createSession(channel, playerName);
                        debug("DEBUG [2.1] 🔐 Premium session created for " + playerName);

                        byte[] verifyToken = sessionManager.getVerifyToken(channel);
                        debug("DEBUG [2.2] 🎫 Verify token generated.");

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
                                debug("DEBUG [2.3] 🔒 Encryption Request shouldAuthenticate=true");
                            }

                            // === WAŻNY DEBUG ===
                            try {
                                MessageDigest md = MessageDigest.getInstance("SHA-1");
                                debug("DEBUG [2.4] 🔑 Our public key length: " + pubKeyBytes.length);
                                debug("DEBUG [2.5] 🔑 Our public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(pubKeyBytes))));

                                byte[] packetKey = encryptionRequest.getByteArrays().read(0);
                                debug("DEBUG [2.6] 🔑 Packet public key length: " + packetKey.length);
                                debug("DEBUG [2.7] 🔑 Packet public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(packetKey))));
                                debug("DEBUG [2.8] 🔑 Keys match: " + Arrays.equals(pubKeyBytes, packetKey));
                            } catch (Exception ex) {
                                debugWarning("DEBUG [2.ERR] ⚠️ Failed to verify public key: " + ex.getMessage());
                            }
                            // === KONIEC WAŻNEGO DEBUG ===

                            protocolManager.sendServerPacket(player, encryptionRequest);
                            debug("DEBUG [2.9] 📤 Encryption Request sent to " + playerName);

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
                debug("DEBUG [3.0] 📥 Received ENCRYPTION_BEGIN packet from client.");

                Player player = event.getPlayer();

                if (player == null) {
                    debugWarning("DEBUG [3.1] ❌ Player is null!");
                    return;
                }

                // Pobieramy Channel od razu
                Channel channel = nettyExtractor.extractChannelFromEvent(event);

                if (channel == null) {
                    debugSevere("DEBUG [3.2] ❌ Could not extract Netty channel");
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

                debug("DEBUG [3.3] 🔎 Session lookup: " +
                        "channel=" + channel +
                        ", pendingUsername=" + username +
                        ", tokenPresent=" + (expectedToken != null));

                if (username == null || expectedToken == null) {
                    debug("DEBUG [3.4] ⏭️ No premium session for channel - bypassing.");
                    return;
                }

                // Anulujemy pakiet natychmiast
                event.setCancelled(true);

                // Pobieramy dane pakietu
                PacketContainer packet = event.getPacket();

                debug("DEBUG [4.0] 📦 ByteArrays size: " + packet.getByteArrays().getValues().size());
                debug("DEBUG [4.1] 📦 Packet structure: " + packet.getModifier());

                for (int i = 0; i < packet.getModifier().size(); i++) {
                    try {
                        Object value = packet.getModifier().read(i);
                        debug("DEBUG [4.2] 📦 Field " + i + ": "
                                + (value != null ? value.getClass().getName() : "null"));
                    } catch (Exception e) {
                        debugWarning("DEBUG [4.2] ⚠️ Field " + i + ": <read failed: "
                                + e.getClass().getSimpleName() + ">");
                    }
                }

                Object secondField = packet.getModifier().read(1);
                debug("DEBUG [4.3] 📦 Field 1 class: " + secondField.getClass().getName());
                debug("DEBUG [4.4] 📦 Field 1 toString: " + secondField);

                byte[] encryptedSecret;
                byte[] encryptedToken;

                try {
                    List<byte[]> byteArrays = packet.getByteArrays().getValues();

                    encryptedSecret = byteArrays.get(0);

                    if (byteArrays.size() >= 2) {
                        encryptedToken = byteArrays.get(1);
                    } else {
                        try {
                            Method getLeft = secondField.getClass().getDeclaredMethod("left");
                            getLeft.setAccessible(true);

                            Object left = getLeft.invoke(secondField);

                            if (!(left instanceof java.util.Optional<?> optional)) {
                                throw new IllegalStateException(
                                        "Either.Left did not return Optional: "
                                                + (left != null ? left.getClass().getName() : "null")
                                );
                            }

                            Object tokenValue = optional.orElse(null);

                            if (!(tokenValue instanceof byte[] token)) {
                                throw new IllegalStateException(
                                        "Either.Left Optional does not contain byte[]: "
                                                + (tokenValue != null ? tokenValue.getClass().getName() : "null")
                                );
                            }

                            encryptedToken = token;
                        } catch (NoSuchMethodException e) {
                            throw new IllegalStateException(
                                    "Could not extract Left value from Either: "
                                            + secondField.getClass().getName(), e
                            );
                        }
                    }
                } catch (Exception e) {
                    sessionManager.removeSession(channel);

                    debugSevere(
                            "DEBUG [4.ERR] ❌ Failed to read byte arrays from packet: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );

                    e.printStackTrace();
                    return;
                }

                debug("DEBUG [4.5] 🔐 Encrypted secret/token successfully extracted.");

                if (encryptedSecret == null || encryptedToken == null) {
                    sessionManager.removeSession(channel);
                    debugWarning("DEBUG [4.ERR] ❌ Null encryption data received from " + clientAddress);
                    return;
                }

                final byte[] finalEncryptedSecret = encryptedSecret;
                final byte[] finalEncryptedToken = encryptedToken;

                // Przechodzimy do wykonywania zadań asynchronicznych z pobranymi danymi
                PacketListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    debug("DEBUG [5.0] 🔓 Starting RSA decryption for " + username);

                    SecretKey sharedSecret;
                    byte[] decryptedToken;

                    try {
                        sharedSecret = EncryptionUtils.decryptSharedSecret(keyPair.getPrivate(), finalEncryptedSecret);
                        decryptedToken = EncryptionUtils.decrypt(keyPair.getPrivate(), finalEncryptedToken);
                    } catch (Exception e) {
                        sessionManager.removeSession(channel);
                        plugin.getLogger().severe("[5.ERR] ❌ Exception during RSA decryption: "
                                + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        channel.close();
                        return;
                    }

                    debug("DEBUG [5.1] 🔓 RSA decryption completed.");

                    if (sharedSecret == null || decryptedToken == null) {
                        sessionManager.removeSession(channel);
                        debugWarning("DEBUG [5.ERR] ❌ Failed to decrypt shared secret or verify token.");
                        channel.close();
                        return;
                    }

                    boolean tokenMatches = Arrays.equals(expectedToken, decryptedToken);

                    debug("DEBUG [5.2] 🎫 Verify token match: " + tokenMatches);

                    if (!tokenMatches) {
                        sessionManager.removeSession(channel);
                        debugWarning("DEBUG [5.ERR] ❌ Verify token mismatch for " + clientAddress + "!");
                        channel.close();
                        return;
                    }

                    sessionManager.removeSession(channel);

                    debug("DEBUG [5.3] 🧹 Premium session removed.");

                    debug("DEBUG [5.4] 🔑 SharedSecret raw bytes length: " + sharedSecret.getEncoded().length);
                    debug("DEBUG [5.5] 🔑 PublicKey raw bytes length: " +
                            (keyPair.getPublic() != null ? keyPair.getPublic().getEncoded().length : "null"));

                    try {
                        String serverId = "";
                        String serverHash = EncryptionUtils.generateServerHash(serverId, keyPair.getPublic(), sharedSecret);

                        debug("DEBUG [6.0] 🔢 serverId='" + serverId + "'");
                        debug("DEBUG [6.1] 🔢 sharedSecretLength=" + sharedSecret.getEncoded().length);
                        debug("DEBUG [6.2] 🔢 publicKeyLength=" + keyPair.getPublic().getEncoded().length);
                        debug("DEBUG [6.3] 🔢 serverHash=" + serverHash);

                        debug("DEBUG [6.4] 🌐 Querying Mojang hasJoined API for " + username);

                        WrappedGameProfile mojangProfile = mojangApi.fetchMojangProfile(username, serverHash);

                        debug("DEBUG [6.5] 🌐 Mojang response: " +
                                (mojangProfile != null ? "PREMIUM PROFILE FOUND" : "NO PREMIUM SESSION"));

                        if (mojangProfile == null) {
                            debugWarning("DEBUG [7.2] ⚠️ No Mojang session found for " + username +
                                    ". Falling back to offline login.");

                            sessionManager.removeSession(channel);

                            // Generate standard offline profile (v3 UUID)
                            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            GameProfile offlineNmsProfile = new GameProfile(offlineUuid, username);
                            WrappedGameProfile offlineWrappedProfile = WrappedGameProfile.fromHandle(offlineNmsProfile);

                            // Mark as non-premium so AstraLogin forces password check
                            PacketListener.this.plugin.getPremiumManager().setAuthenticated(offlineUuid, false);

                            debug("DEBUG [7.3] 🔐 Enabling AES encryption on offline channel.");

                            // CRITICAL: We MUST enable AES encryption on Netty pipeline, because client already enabled it!
                            nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                                PacketListener.this.plugin.getSchedulerManager().runSync(() -> {
                                    new LoginCompleter(PacketListener.this.plugin)
                                            .complete(channel, offlineUuid, offlineWrappedProfile);
                                });

                                debug("DEBUG [7.4] 🔓 Encryption injected and offline login completed for " + username);
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
                            // Gdyby pole nazywało się inaczej
                            plugin.getLogger().severe("Could not extract UUID from GameProfile: " + e.getMessage());
                        }

                        // Wstrzykujemy szyfrowanie bezpośrednio do obiektu Channel
                        debug("DEBUG [7.0] 🔐 Enabling AES encryption on premium channel.");

                        UUID finalUUID = uuid;

                        nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                            debug("DEBUG [7.1] 🚀 Passing premium profile to LoginCompleter.");

                            // Dokończenie logowania gracza wywoływane jest synchronicznie bo jest inaczej niebezpiecznie na nowszych wersjach Paper'a
                            PacketListener.this.plugin.getSchedulerManager().runSync(() -> {
                                new LoginCompleter(PacketListener.this.plugin)
                                        .complete(channel, finalUUID, mojangProfile);
                            });
                        });

                    } catch (Exception e) {
                        plugin.getLogger().severe("[6.ERR] ❌ Error during Mojang premium verification: "
                                + e.getClass().getName() + ": " + e.getMessage());
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