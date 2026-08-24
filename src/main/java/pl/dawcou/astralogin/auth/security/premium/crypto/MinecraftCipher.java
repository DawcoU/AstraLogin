package pl.dawcou.astralogin.auth.security.premium.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;

public class MinecraftCipher {

    private final Cipher cipher;

    public MinecraftCipher(int mode, SecretKey key) throws GeneralSecurityException {
        cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(key.getEncoded()));
    }

    public byte[] transform(byte[] input) throws GeneralSecurityException {
        return cipher.update(input);
    }
}