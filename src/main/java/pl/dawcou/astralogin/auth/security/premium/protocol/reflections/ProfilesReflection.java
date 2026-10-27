
package pl.dawcou.astralogin.auth.security.premium.protocol.reflections;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfilesReflection {

    private static final Logger LOGGER =
            Logger.getLogger(ProfilesReflection.class.getName());

    private ProfilesReflection() {}

    public static UUID getProfileId(GameProfile profile) {
        if (profile == null) {
            return null;
        }

        try {
            Object result = invokeProfileAccessor(
                    profile, "id", "getId"
            );
            if (result instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // Spróbuj odczytu pola.
        }

        try {
            Field field = findField(profile.getClass(), "id");
            if (field != null) {
                field.setAccessible(true);
                Object result = field.get(profile);
                return result instanceof UUID uuid ? uuid : null;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Nie udało się odczytać UUID.
        }

        return null;
    }

    public static String getProfileName(GameProfile profile) {
        if (profile == null) {
            return "Unknown";
        }

        try {
            Object result = invokeProfileAccessor(
                    profile, "name", "getName"
            );
            if (result instanceof String name && !name.isBlank()) {
                return name;
            }
        } catch (ReflectiveOperationException ignored) {
            // Spróbuj odczytu pola.
        }

        try {
            Field field = findField(profile.getClass(), "name");
            if (field != null) {
                field.setAccessible(true);
                Object result = field.get(profile);
                if (result instanceof String name && !name.isBlank()) {
                    return name;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Nie udało się odczytać nazwy.
        }

        return "Unknown";
    }

    /**
     * Tworzy NOWY GameProfile z dodatkową właściwością.
     * Nie próbuje modyfikować mapy należącej do oryginalnego profilu.
     *
     * Zwraca null w razie błędu.
     */
    public static GameProfile withProfileProperty(
            GameProfile profile,
            String name,
            String value,
            String signature
    ) {
        if (profile == null || name == null || value == null) {
            return null;
        }

        try {
            Object originalProperties = getProfileProperties(profile);

            if (originalProperties == null) {
                throw new IllegalStateException(
                        "GameProfile returned null properties."
                );
            }

            ClassLoader classLoader = profile.getClass().getClassLoader();

            Class<?> propertyMapClass = Class.forName(
                    "com.mojang.authlib.properties.PropertyMap",
                    true,
                    classLoader
            );

            // Utwórz modyfikowalną multimapę z classloadera serwera.
            Object mutable = createMutableMultimap(
                    propertyMapClass.getClassLoader()
            );

            Method mutablePut = mutable.getClass().getMethod(
                    "put", Object.class, Object.class
            );

            // Skopiuj właściwości istniejącego profilu.
            Method originalEntriesMethod =
                    originalProperties.getClass().getMethod("entries");

            Object originalEntriesResult =
                    originalEntriesMethod.invoke(originalProperties);

            if (!(originalEntriesResult instanceof Iterable<?> originalEntries)) {
                throw new IllegalStateException(
                        "PropertyMap.entries() did not return an Iterable."
                );
            }

            for (Object item : originalEntries) {
                if (item instanceof Map.Entry<?, ?> entry) {
                    mutablePut.invoke(
                            mutable,
                            entry.getKey(),
                            entry.getValue()
                    );
                }
            }

            // Utwórz nową właściwość w wersji Authlib serwera.
            Object property = createProperty(
                    classLoader,
                    name,
                    value,
                    signature
            );

            mutablePut.invoke(mutable, name, property);

            // Utwórz PropertyMap — obsłuż oba warianty konstruktora.
            Object newProperties = null;

            for (Constructor<?> constructor : propertyMapClass.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();

                if (parameters.length == 1
                        && parameters[0].isInstance(mutable)) {
                    newProperties = constructor.newInstance(mutable);
                    break;
                }
            }

            if (newProperties == null) {
                Constructor<?> emptyConstructor =
                        propertyMapClass.getConstructor();

                newProperties = emptyConstructor.newInstance();

                Method propertyMapPut = propertyMapClass.getMethod(
                        "put", Object.class, Object.class
                );

                Method mutableEntriesMethod =
                        mutable.getClass().getMethod("entries");

                Object mutableEntriesResult =
                        mutableEntriesMethod.invoke(mutable);

                if (!(mutableEntriesResult instanceof Iterable<?> mutableEntries)) {
                    throw new IllegalStateException(
                            "Multimap.entries() did not return an Iterable."
                    );
                }

                for (Object item : mutableEntries) {
                    if (item instanceof Map.Entry<?, ?> entry) {
                        propertyMapPut.invoke(
                                newProperties,
                                entry.getKey(),
                                entry.getValue()
                        );
                    }
                }
            }

            // Spróbuj najpierw konstruktora z PropertyMap (nowsze Authlib).
            Constructor<?> profileConstructor = null;

            for (Constructor<?> constructor : profile.getClass().getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();

                if (parameters.length == 3
                        && parameters[0] == UUID.class
                        && parameters[1] == String.class
                        && parameters[2].isInstance(newProperties)) {
                    profileConstructor = constructor;
                    break;
                }
            }

            if (profileConstructor != null) {
                return (GameProfile) profileConstructor.newInstance(
                        getProfileId(profile),
                        getProfileName(profile),
                        newProperties
                );
            }

            // Starsze Authlib: użyj konstruktora dwuargumentowego.
            Constructor<?> legacyConstructor = profile.getClass().getConstructor(
                    UUID.class,
                    String.class
            );

            GameProfile newProfile = (GameProfile) legacyConstructor.newInstance(
                    getProfileId(profile),
                    getProfileName(profile)
            );

            Object targetProperties = getProfileProperties(newProfile);

            Method targetPut = targetProperties.getClass().getMethod(
                    "put", Object.class, Object.class
            );

            Method sourceEntries = newProperties.getClass().getMethod("entries");
            Object sourceEntriesResult = sourceEntries.invoke(newProperties);

            if (!(sourceEntriesResult instanceof Iterable<?> sourceEntriesIterable)) {
                throw new IllegalStateException(
                        "PropertyMap.entries() did not return an Iterable."
                );
            }

            for (Object item : sourceEntriesIterable) {
                if (item instanceof Map.Entry<?, ?> entry) {
                    targetPut.invoke(
                            targetProperties,
                            entry.getKey(),
                            entry.getValue()
                    );
                }
            }

            return newProfile;

        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            LOGGER.log(
                    Level.SEVERE,
                    "Authlib constructor or method invocation failed.",
                    cause
            );
            return null;

        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Could not create GameProfile with property.",
                    e
            );
            return null;
        }
    }

    private static Object createMutableMultimap(
            ClassLoader classLoader
    ) throws ReflectiveOperationException {

        Class<?> multimapClass = Class.forName(
                "com.google.common.collect.ArrayListMultimap",
                true,
                classLoader
        );

        Method createMethod = multimapClass.getMethod("create");

        return createMethod.invoke(null);
    }

    public static boolean hasProfileProperty(
            GameProfile profile,
            String expectedName,
            String expectedValue
    ) {
        if (profile == null
                || expectedName == null
                || expectedValue == null) {
            return false;
        }

        try {
            Object properties = getProfileProperties(profile);

            Method getMethod =
                    properties.getClass().getMethod("get", Object.class);

            Object result = getMethod.invoke(properties, expectedName);

            if (!(result instanceof Collection<?> values)) {
                return false;
            }

            for (Object property : values) {
                if (expectedValue.equals(readPropertyValue(property))) {
                    return true;
                }
            }

            return false;

        } catch (ReflectiveOperationException
                 | RuntimeException e) {

            LOGGER.log(
                    Level.WARNING,
                    "Could not read GameProfile property.",
                    e
            );
            return false;
        }
    }

    private static Object getProfileProperties(
            GameProfile profile
    ) throws ReflectiveOperationException {

        for (String methodName :
                new String[]{"properties", "getProperties"}) {
            try {
                Method method = profile.getClass().getMethod(methodName);
                return method.invoke(profile);
            } catch (NoSuchMethodException ignored) {
                // Sprawdź starsze lub nowsze API.
            }
        }

        Field field = findField(profile.getClass(), "properties");

        if (field != null) {
            field.setAccessible(true);
            return field.get(profile);
        }

        throw new NoSuchFieldException(
                "No properties(), getProperties() or properties field in "
                        + profile.getClass().getName()
        );
    }

    private static Object createProperty(
            ClassLoader classLoader,
            String name,
            String value,
            String signature
    ) throws ReflectiveOperationException {

        Class<?> propertyClass = Class.forName(
                "com.mojang.authlib.properties.Property",
                true,
                classLoader
        );

        Constructor<?> twoArgumentConstructor = null;
        Constructor<?> threeArgumentConstructor = null;

        for (Constructor<?> constructor :
                propertyClass.getConstructors()) {

            Class<?>[] parameters = constructor.getParameterTypes();

            if (parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == String.class) {
                twoArgumentConstructor = constructor;
            }

            if (parameters.length == 3
                    && parameters[0] == String.class
                    && parameters[1] == String.class
                    && parameters[2] == String.class) {
                threeArgumentConstructor = constructor;
            }
        }

        if (signature != null) {
            if (threeArgumentConstructor == null) {
                throw new NoSuchMethodException(
                        "Property constructor with signature not found."
                );
            }

            return threeArgumentConstructor.newInstance(
                    name, value, signature
            );
        }

        if (twoArgumentConstructor != null) {
            return twoArgumentConstructor.newInstance(name, value);
        }

        if (threeArgumentConstructor != null) {
            return threeArgumentConstructor.newInstance(
                    name, value, null
            );
        }

        throw new NoSuchMethodException(
                "No compatible public Property constructor found."
        );
    }

    private static Object readPropertyValue(
            Object property
    ) throws ReflectiveOperationException {

        for (String methodName : new String[]{"value", "getValue"}) {
            try {
                return property.getClass()
                        .getMethod(methodName)
                        .invoke(property);
            } catch (NoSuchMethodException ignored) {
                // Sprawdź następną nazwę.
            }
        }

        Field field = findField(property.getClass(), "value");

        if (field != null) {
            field.setAccessible(true);
            return field.get(property);
        }

        throw new NoSuchMethodException(
                "No value accessor in " + property.getClass().getName()
        );
    }

    private static Object invokeProfileAccessor(
            GameProfile profile,
            String modernName,
            String legacyName
    ) throws ReflectiveOperationException {

        try {
            return profile.getClass().getMethod(modernName).invoke(profile);
        } catch (NoSuchMethodException ignored) {
            return profile.getClass().getMethod(legacyName).invoke(profile);
        }
    }

    private static Field findField(
            Class<?> type,
            String fieldName
    ) {
        for (Class<?> current = type;
             current != null;
             current = current.getSuperclass()) {

            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // Szukaj w nadklasie.
            }
        }

        return null;
    }
}
