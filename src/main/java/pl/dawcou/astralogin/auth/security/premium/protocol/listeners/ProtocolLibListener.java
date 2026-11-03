package pl.dawcou.astralogin.auth.security.premium.protocol.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.premium.api.MojangApiService;
import pl.dawcou.astralogin.auth.security.premium.protocol.LoginCompleter;
import pl.dawcou.astralogin.auth.security.premium.protocol.NettyChannelExtractor;
import pl.dawcou.astralogin.auth.security.premium.protocol.cipher.EncryptionUtils;
import pl.dawcou.astralogin.auth.security.premium.protocol.reflections.ProfilesReflection;
import pl.dawcou.astralogin.auth.security.premium.session.PremiumSessionManager;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ProtocolLibListener implements Listener {

    private final AstraLogin plugin;
    private final ProtocolManager protocolManager;
    private final KeyPair keyPair;

    private final MojangApiService mojangApi;
    private final NettyChannelExtractor nettyExtractor;
    private final PremiumSessionManager sessionManager;

    public ProtocolLibListener(AstraLogin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.keyPair = plugin.getKeyPair();

        this.mojangApi = new MojangApiService(plugin);
        this.nettyExtractor = new NettyChannelExtractor(plugin);
        this.sessionManager = new PremiumSessionManager();

        registerListeners();
    }

    private void registerListeners() {
        // --- 1. ODBIERANIE PACKET START & WYSYŁANIE ENCRYPTION REQUEST ---
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.0] 📥 Received START packet.");

                boolean premiumAllowed = ProtocolLibListener.this.plugin.getPremiumManager().premiumLoginRequirements("FULL");
                if (!premiumAllowed) {
                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.1] ⏭️ Premium login is not allowed.");
                    return;
                }

                Player player = event.getPlayer();
                String username;

                try {
                    username = event.getPacket().getStrings().read(0);
                } catch (Exception e) {
                    try {
                        // 1. Odczytujemy obiekt jako WrappedGameProfile z ProtocolLiba
                        com.comphenix.protocol.wrappers.WrappedGameProfile wrappedProfile = event.getPacket().getGameProfiles().readSafely(0);

                        if (wrappedProfile == null || wrappedProfile.getName() == null) return;

                        username = wrappedProfile.getName();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return;
                    }
                }

                final String playerName = username;

                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.2] 👤 Username received: " + username);

                // Wyciągamy adres i Channel na głównym wątku
                Channel channel = extractChannelFromEvent(event);

                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.3] 🔌 Netty channel extracted: " + channel);

                event.setCancelled(true);

                String finalUsername = username;
                ProtocolLibListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    boolean isAccountPremium = ProtocolLibListener.this.plugin.getPremiumManager().isUsernamePremium(playerName);

                    if (!isAccountPremium) {
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.6] ⏭️ Player is not premium or check failed. Checking database fallback.");

                        // Szukamy istniejącego UUID gracza w bazie po nicku (ratuje weteranów przy awarii Mojanga)
                        UUID playerUuid = ProtocolLibListener.this.plugin.getAccountManager().getUuidByUsername(finalUsername);

                        if (playerUuid != null) {
                            ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.6.1] 🛡️ Fallback: Found existing UUID for " + finalUsername + " in database!");
                        } else {
                            // Jeśli gracz jest całkowicie nowy, generujemy standardowy offline UUID
                            playerUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + finalUsername).getBytes(StandardCharsets.UTF_8));
                            ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [1.6.2] 🆕 New non-premium player. Generated offline UUID.");
                        }

                        // Ustawiamy stan na DENIED (musi się zalogować zwykłym /login)
                        ProtocolLibListener.this.plugin.getPremiumManager().setAuthenticated(playerUuid, false);

                        // Wyciągamy adres IP do eventu
                        InetAddress address = player.getAddress() != null ? player.getAddress().getAddress() : null;

                        // Obsługa logowania offline
                        final UUID finalOfflineUuid = playerUuid;
                        handleOfflineLogin(player, channel, address, finalOfflineUuid, playerName);
                        return;
                    }

                    ProtocolLibListener.this.plugin.getSchedulerManager().runSync(() -> {
                        if (channel != null) {
                            ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.0] 🔌 BEFORE ENCRYPTION_BEGIN pipeline=" + channel.pipeline().names());
                        } else {
                            ProtocolLibListener.this.plugin.getPremiumManager().debugSevere("DEBUG [2.0.ERR] ❌ Could not extract channel for " + playerName);
                            return;
                        }

                        sessionManager.createSession(channel, playerName);
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.1] 🔐 Premium session created for " + playerName);

                        byte[] verifyToken = sessionManager.getVerifyToken(channel);
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.2] 🎫 Verify token generated.");

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
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.3] 🔒 Encryption Request shouldAuthenticate=true");
                            }

                            // === WAŻNY DEBUG ===
                            try {
                                MessageDigest md = MessageDigest.getInstance("SHA-1");
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.4] 🔑 Our public key length: " + pubKeyBytes.length);
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.5] 🔑 Our public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(pubKeyBytes))));

                                byte[] packetKey = encryptionRequest.getByteArrays().read(0);
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.6] 🔑 Packet public key length: " + packetKey.length);
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.7] 🔑 Packet public key SHA1: " +
                                        String.format("%040x", new BigInteger(1, md.digest(packetKey))));
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.8] 🔑 Keys match: " + Arrays.equals(pubKeyBytes, packetKey));
                            } catch (Exception ex) {
                                ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [2.ERR] ⚠️ Failed to verify public key: " + ex.getMessage());
                            }
                            // === KONIEC WAŻNEGO DEBUG ===

                            protocolManager.sendServerPacket(player, encryptionRequest);
                            ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [2.9] 📤 Encryption Request sent to " + playerName);

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
                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [3.0] 📥 Received ENCRYPTION_BEGIN packet from client.");

                Player player = event.getPlayer();

                if (player == null) {
                    ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [3.1] ❌ Player is null!");
                    return;
                }

                // Pobieramy Channel od razu
                Channel channel = extractChannelFromEvent(event);

                if (channel == null) {
                    ProtocolLibListener.this.plugin.getPremiumManager().debugSevere("DEBUG [3.2] ❌ Could not extract Netty channel");
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

                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [3.3] 🔎 Session lookup: " +
                        "channel=" + channel +
                        ", pendingUsername=" + username +
                        ", tokenPresent=" + (expectedToken != null));

                if (username == null || expectedToken == null) {
                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [3.4] ⏭️ No premium session for channel - bypassing.");
                    return;
                }

                // Anulujemy pakiet natychmiast
                event.setCancelled(true);

                // Pobieramy dane pakietu
                PacketContainer packet = event.getPacket();

                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.0] 📦 ByteArrays size: " + packet.getByteArrays().getValues().size());
                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.1] 📦 Packet structure: " + packet.getModifier());

                for (int i = 0; i < packet.getModifier().size(); i++) {
                    try {
                        Object value = packet.getModifier().read(i);
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.2] 📦 Field " + i + ": "
                                + (value != null ? value.getClass().getName() : "null"));
                    } catch (Exception e) {
                        ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [4.2] ⚠️ Field " + i + ": <read failed: "
                                + e.getClass().getSimpleName() + ">");
                    }
                }

                Object secondField = packet.getModifier().read(1);
                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.3] 📦 Field 1 class: " + secondField.getClass().getName());
                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.4] 📦 Field 1 toString: " + secondField);

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

                    ProtocolLibListener.this.plugin.getPremiumManager().debugSevere(
                            "DEBUG [4.ERR] ❌ Failed to read byte arrays from packet: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );

                    e.printStackTrace();
                    return;
                }

                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [4.5] 🔐 Encrypted secret/token successfully extracted.");

                if (encryptedSecret == null || encryptedToken == null) {
                    sessionManager.removeSession(channel);
                    ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [4.ERR] ❌ Null encryption data received from " + clientAddress);
                    return;
                }

                final byte[] finalEncryptedSecret = encryptedSecret;
                final byte[] finalEncryptedToken = encryptedToken;

                // Przechodzimy do wykonywania zadań asynchronicznych z pobranymi danymi
                ProtocolLibListener.this.plugin.getSchedulerManager().runAsync(() -> {
                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.0] 🔓 Starting RSA decryption for " + username);

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

                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.1] 🔓 RSA decryption completed.");

                    if (sharedSecret == null || decryptedToken == null) {
                        sessionManager.removeSession(channel);
                        ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [5.ERR] ❌ Failed to decrypt shared secret or verify token.");
                        channel.close();
                        return;
                    }

                    boolean tokenMatches = Arrays.equals(expectedToken, decryptedToken);

                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.2] 🎫 Verify token match: " + tokenMatches);

                    if (!tokenMatches) {
                        sessionManager.removeSession(channel);
                        ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [5.ERR] ❌ Verify token mismatch for " + clientAddress + "!");
                        channel.close();
                        return;
                    }

                    sessionManager.removeSession(channel);

                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.3] 🧹 Premium session removed.");

                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.4] 🔑 SharedSecret raw bytes length: " + sharedSecret.getEncoded().length);
                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [5.5] 🔑 PublicKey raw bytes length: " +
                            (keyPair.getPublic() != null ? keyPair.getPublic().getEncoded().length : "null"));

                    try {
                        String serverId = "";
                        String serverHash = EncryptionUtils.generateServerHash(serverId, keyPair.getPublic(), sharedSecret);

                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.0] 🔢 serverId='" + serverId + "'");
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.1] 🔢 sharedSecretLength=" + sharedSecret.getEncoded().length);
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.2] 🔢 publicKeyLength=" + keyPair.getPublic().getEncoded().length);
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.3] 🔢 serverHash=" + serverHash);

                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.4] 🌐 Querying Mojang hasJoined API for " + username);

                        GameProfile mojangProfile = mojangApi.fetchMojangProfile(username, serverHash);

                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [6.5] 🌐 Mojang response: " +
                                (mojangProfile != null ? "PREMIUM PROFILE FOUND" : "NO PREMIUM SESSION"));

                        if (mojangProfile == null) {
                            ProtocolLibListener.this.plugin.getPremiumManager().debugWarning("DEBUG [7.2] ⚠️ No Mojang session found for " + username +
                                    ". Falling back to offline login.");

                            sessionManager.removeSession(channel);

                            // Szukamy istniejącego UUID w bazie lub generujemy nowy offline UUID
                            UUID playerUuid = ProtocolLibListener.this.plugin.getAccountManager().getUuidByUsername(username);

                            if (playerUuid != null) {
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [7.2.1] 🛡️ Fallback: Found existing UUID for " + username + " in database!");
                            } else {
                                playerUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                                ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [7.2.2] 🆕 Fallback: New player, generated offline UUID.");
                            }

                            InetAddress address = socketAddress != null ? socketAddress.getAddress() : null;

                            // Kluczowe: Najpierw włączamy szyfrowanie AES na kanale (bo klient je włączył)
                            UUID finalPlayerUuid = playerUuid;
                            nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                                handleOfflineLogin(player, channel, address, finalPlayerUuid, username);
                            });
                            return;
                        }

                        UUID finalUUID = ProfilesReflection.getProfileId(mojangProfile);
                        String finalName = ProfilesReflection.getProfileName(mojangProfile);
                        if (finalName.equals("Unknown")) {
                            finalName = username;
                        }

                        // Wstrzykujemy szyfrowanie bezpośrednio do obiektu Channel
                        ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [7.0] 🔐 Enabling AES encryption on premium channel.");

                        String finalUsername = finalName;
                        nettyExtractor.enableEncryptionOnChannel(channel, sharedSecret, () -> {
                            ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [7.1] 🚀 Passing premium profile to LoginCompleter.");

                            try {
                                InetAddress address = ((java.net.InetSocketAddress) channel.remoteAddress()).getAddress();

                                AsyncPlayerPreLoginEvent preLoginEvent =
                                        new AsyncPlayerPreLoginEvent(
                                                finalUsername,
                                                address,
                                                finalUUID
                                        );

                                Bukkit.getPluginManager().callEvent(preLoginEvent);

                                if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                                    ProtocolLibListener.this.plugin.getPremiumManager().debug("DEBUG [7.2] ❌ Pre-login event denied connection: " + preLoginEvent.getKickMessage());
                                    sendLoginKick(player, channel, preLoginEvent.getKickMessage());
                                    return;
                                }

                                // Sync completion
                                ProtocolLibListener.this.plugin.getSchedulerManager().runSync(() -> new LoginCompleter(ProtocolLibListener.this.plugin)
                                        .complete(channel, finalUUID, mojangProfile));

                            } catch (Exception e) {
                                plugin.getLogger().severe("[7.ERR] ❌ Error during AsyncPlayerPreLoginEvent execution: "
                                        + e.getClass().getName() + ": " + e.getMessage());
                                e.printStackTrace();
                                if (channel.isOpen()) {
                                    channel.close();
                                }
                            }
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

    private Channel extractChannelFromEvent(PacketEvent event) {
        try {
            org.bukkit.entity.Player player = event.getPlayer();
            if (player == null) return null;

            com.comphenix.protocol.injector.netty.Injector injector =
                    com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory.getInjectorFromPlayer(player);
            if (injector == null) return null;

            com.comphenix.protocol.reflect.accessors.FieldAccessor channelAccessor =
                    com.comphenix.protocol.reflect.accessors.Accessors.getFieldAccessor(injector.getClass(), Channel.class, true);

            return (Channel) channelAccessor.get(injector);
        } catch (Exception e) {
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Obsługa logowania graczy non-premium po wykryciu braku konta premium w API Mojanga
     */
    private void handleOfflineLogin(Player player, Channel channel, InetAddress address, UUID uuid, String username) {
        try {
            GameProfile offlineProfile = new GameProfile(uuid, username);
            plugin.getPremiumManager().setAuthenticated(uuid, false);

            AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(username, address, uuid);
            Bukkit.getPluginManager().callEvent(preLoginEvent);

            if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                sendLoginKick(player, channel, preLoginEvent.getKickMessage());
                return;
            }

            plugin.getSchedulerManager().runSync(() -> new LoginCompleter(plugin).complete(channel, uuid, offlineProfile));
        } catch (Exception e) {
            e.printStackTrace();
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }

    private void sendLoginKick(Player player, Channel channel, String reason) {
        try {
            String message = reason != null ? reason : "Disconnected";

            // Tworzymy pakiet rozłączenia logowania przez ProtocolLib
            PacketContainer disconnectPacket = protocolManager.createPacket(PacketType.Login.Server.DISCONNECT);

            // ProtocolLib posiada wbudowany wrapper na komponenty chatu
            WrappedChatComponent chatComponent =
                    WrappedChatComponent.fromText(message);

            disconnectPacket.getChatComponents().write(0, chatComponent);

            if (player != null) {
                protocolManager.sendServerPacket(player, disconnectPacket);
            }

            if (channel != null && channel.isOpen()) {
                channel.flush();
                channel.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }
}