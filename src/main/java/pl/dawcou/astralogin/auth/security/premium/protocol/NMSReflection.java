package pl.dawcou.astralogin.auth.security.premium.protocol;

import com.mojang.authlib.GameProfile;
import pl.dawcou.astralogin.AstraLogin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class NMSReflection {

    private NMSReflection() {}

    /*--------------------------------------------------------------------------------
     * Helper method to dynamically extract PacketListener across multiple NMS versions
     *--------------------------------------------------------------------------------*/
    public static Object findPacketListener(AstraLogin plugin, Object packetHandler) throws Exception {
        if (packetHandler == null) {
            plugin.getPremiumManager().debug("DEBUG [NMS 1] PacketHandler is null!");
            return null;
        }

        plugin.getPremiumManager().debug("DEBUG [NMS 2] Scanning PacketHandler class: " + packetHandler.getClass().getName());

        // 1. First attempt: Method invocation returning a packet listener
        for (Method method : packetHandler.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getName().contains("PacketListener")) {
                method.setAccessible(true);
                Object listener = method.invoke(packetHandler);
                if (listener != null) {
                    plugin.getPremiumManager().debug("DEBUG [NMS 3] Found PacketListener via method: " + method.getName());
                    return listener;
                }
            }
        }

        // 2. Second attempt: Direct field scan for PacketListener
        for (Class<?> clazz = packetHandler.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().contains("PacketListener")) {
                    field.setAccessible(true);
                    Object listener = field.get(packetHandler);
                    if (listener != null) {
                        plugin.getPremiumManager().debug("DEBUG [NMS 4] Found active PacketListener in field: " + field.getName());
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
            plugin.getPremiumManager().debug("DEBUG [NMS-ERR 1] Provided class is not an enum: " + enumClass.getName());
            plugin.getLogger().severe("[NMS-ERR] Failed to resolve ready state: Target class is not an enum.");
            return null;
        }

        plugin.getPremiumManager().debug("DEBUG [NMS 5] Scanning login state enum: " + enumClass.getName());

        Object[] constants = enumClass.getEnumConstants();
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            if ("READY_TO_ACCEPT".equals(name) || "ACCEPTED".equals(name)) {
                plugin.getPremiumManager().debug("DEBUG [NMS 6] Compatible ready state resolved: " + name);
                return constant;
            }
        }

        plugin.getPremiumManager().debug("DEBUG [NMS-ERR 2] No compatible enum state found in " + enumClass.getName());
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

    public static UUID getProfileId(GameProfile profile) {
        if (profile == null) return null;
        try {
            Method method = profile.getClass().getMethod("id");
            return (UUID) method.invoke(profile);
        } catch (Exception e1) {
            try {
                Method method = profile.getClass().getMethod("getId");
                return (UUID) method.invoke(profile);
            } catch (Exception e2) {
                try {
                    Field field = profile.getClass().getDeclaredField("id");
                    field.setAccessible(true);
                    return (UUID) field.get(profile);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    public static String getProfileName(GameProfile profile) {
        if (profile == null) {
            return "Unknown";
        }
        try {
            Method method = profile.getClass().getMethod("name");
            String name = (String) method.invoke(profile);
            return name != null ? name : "Unknown";
        } catch (Exception e1) {
            try {
                Method method = profile.getClass().getMethod("getName");
                String name = (String) method.invoke(profile);
                return name != null ? name : "Unknown";
            } catch (Exception e2) {
                try {
                    Field field = profile.getClass().getDeclaredField("name");
                    field.setAccessible(true);
                    String name = (String) field.get(profile);
                    return name != null ? name : "Unknown";
                } catch (Exception e3) {
                    return "Unknown";
                }
            }
        }
    }
}