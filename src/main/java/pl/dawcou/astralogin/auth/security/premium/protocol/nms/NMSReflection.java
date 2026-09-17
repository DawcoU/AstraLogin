package pl.dawcou.astralogin.auth.security.premium.protocol.nms;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import pl.dawcou.astralogin.AstraLogin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NMSReflection {

    private NMSReflection() {}

    /*--------------------------------------------------------------------------------
     * Helper method to dynamically extract PacketListener across multiple NMS versions
     *--------------------------------------------------------------------------------*/
    public static Object findPacketListener(AstraLogin plugin, Object packetHandler) throws Exception {
        if (packetHandler == null) {
            plugin.getPacketListener().debug("DEBUG [NMS] Scanning PacketHandler: "
                    + packetHandler.getClass().getName());
            return null;
        }

        plugin.getPacketListener().debug("DEBUG [NMS] PacketHandler class: "
                + packetHandler.getClass().getName());

        // 1. First attempt: Method invocation (Mojang mapped / 1.21+)
        for (Method method : packetHandler.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0
                    && "net.minecraft.network.PacketListener".equals(method.getReturnType().getName())) {
                method.setAccessible(true);
                Object listener = method.invoke(packetHandler);
                if (listener != null) {
                    plugin.getPacketListener().debug("DEBUG [NMS] Found PacketListener via method: "
                            + method.getName());
                    return listener;
                }
            }
        }

        // 2. Second attempt: Direct non-null field scan (Fallback for 1.18 - 1.20.4)
        for (Class<?> clazz = packetHandler.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().equals("net.minecraft.network.PacketListener")) {
                    field.setAccessible(true);
                    Object listener = field.get(packetHandler);

                    // Skip internal unassigned/null fields (e.g. field 'p' in 1.20.4)
                    if (listener != null) {
                        plugin.getPacketListener().debug("DEBUG [NMS] Found active PacketListener in field: "
                                + field.getName());
                        return listener;
                    }
                }
            }
        }

        plugin.getLogger().severe("Could not resolve PacketListener using method or field lookup!");
        return null;
    }

    public static Field findFieldByType(Class<?> clazz, Class<?> type) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().equals(type)) {
                    return field;
                }
            }
        }

        return null;
    }

    public static Field findLoginStateField(Class<?> clazz) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().isEnum()) {
                    return field;
                }
            }
        }

        return null;
    }

    public static Object findReadyState(AstraLogin plugin, Class<?> enumClass) {
        if (!enumClass.isEnum()) {
            plugin.getPacketListener().debug("DEBUG [NMS-ERR] Provided class is not an enum: "
                    + enumClass.getName());
            plugin.getLogger().severe("[NMS-ERR] Failed to resolve ready state: Target class is not an enum.");
            return null;
        }

        plugin.getPacketListener().debug("DEBUG [NMS] Scanning login state enum: "
                + enumClass.getName());

        Object[] constants = enumClass.getEnumConstants();

        for (Object constant : constants) {
            plugin.getPacketListener().debug("DEBUG [NMS] Evaluated state constant: "
                    + ((Enum<?>) constant).name());
        }

        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();

            if ("READY_TO_ACCEPT".equals(name) || "ACCEPTED".equals(name)) {
                plugin.getPacketListener().debug("DEBUG [NMS] Compatible ready state resolved: "
                        + name);
                return constant;
            }
        }

        plugin.getPacketListener().debug("DEBUG [NMS-ERR] No compatible enum state found in "
                + enumClass.getName());

        plugin.getLogger().severe("Could not find READY_TO_ACCEPT or ACCEPTED state constant!");
        return null;
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    public static GameProfile toNmsProfile(WrappedGameProfile wrapped) {
        if (wrapped != null && wrapped.getHandle() instanceof GameProfile directProfile) {
            return directProfile;
        }

        return new GameProfile(
                UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Unknown".getBytes(StandardCharsets.UTF_8)
                ),
                "Unknown"
        );
    }

    public static String getProfileName(GameProfile profile) {
        if (profile == null) {
            return "Unknown";
        }

        try {
            Field nameField = GameProfile.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(profile);
        } catch (Exception e) {
            return "Unknown";
        }
    }
}