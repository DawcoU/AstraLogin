package pl.dawcou.astralogin;

import org.mindrot.jbcrypt.BCrypt;

public class HashPassword {

    // Używasz przy /register
    public static String hash(String password) {
        try {
            return BCrypt.hashpw(password, BCrypt.gensalt(10));
        } catch (Exception e) {
            return null;
        }
    }

    // Używasz przy /login
    public static boolean verify(String password, String hashed) {
        try {
            if (hashed == null || !hashed.startsWith("$2a$")) return false;
            return BCrypt.checkpw(password, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}