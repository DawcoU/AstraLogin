package pl.dawcou.astralogin.auth.security.premium.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptionUtil {

    private EncryptionUtil() {}

    // Generuje unikalny Server Hash wariantu Minecraft SHA-1 (Notchian Hex)
    public static String generateServerHash(String serverId, PublicKey publicKey, SecretKey secretKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(serverId.getBytes(StandardCharsets.ISO_8859_1));
            digest.update(secretKey.getEncoded());
            digest.update(publicKey.getEncoded());

            // Konstruktor BigInteger(byte[]) sam załatwia kod uzupełnień do dwóch i znak minusa
            return new BigInteger(digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Odszyfrowuje SharedSecret wysłany przez klienta gracza za pomocą klucza prywatnego RSA
    public static SecretKey decryptSharedSecret(Key privateKey, byte[] encryptedSecret) {
        if (privateKey == null || encryptedSecret == null || encryptedSecret.length == 0) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decrypted = cipher.doFinal(encryptedSecret);
            if (decrypted == null || decrypted.length == 0) {
                return null;
            }

            // Protokół Minecrafta wymaga dokładnie 16-bajtowego klucza AES-128
            byte[] aesKeyBytes = decrypted;
            if (decrypted.length != 16) {
                if (decrypted.length > 16) {
                    // Jeśli padding dodał bajty z przodu, bierzesz dokładnie ostatnie 16 bajtów
                    aesKeyBytes = new byte[16];
                    System.arraycopy(decrypted, decrypted.length - 16, aesKeyBytes, 0, 16);
                } else {
                    return null; // Zły rozmiar klucza
                }
            }

            return new SecretKeySpec(aesKeyBytes, "AES");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Odszyfrowuje zwykłą tablicę bajtów (np. VerifyToken)
    public static byte[] decrypt(Key privateKey, byte[] encryptedData) {
        if (privateKey == null || encryptedData == null || encryptedData.length == 0) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}