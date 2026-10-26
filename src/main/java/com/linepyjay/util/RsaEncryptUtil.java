package com.linepyjay.util;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.HexFormat;

/**
 * RSA encryption utility for LINE password-based login.
 */
public class RsaEncryptUtil {

    /**
     * Encrypts the session key, email, and password using RSA for LINE loginZ.
     */
    public static String rsaEncrypt(String sessionKey, String email, String password,
                                     String nvalueHex, String evalueHex) throws Exception {
        var key = KeyFactory.getInstance("RSA", "BC")
            .generatePublic(new RSAPublicKeySpec(
                new BigInteger(nvalueHex, 16), new BigInteger(evalueHex, 16)));
        byte[] sk = sessionKey.getBytes(StandardCharsets.UTF_8);
        byte[] id = email.getBytes(StandardCharsets.UTF_8);
        byte[] pw = password.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[1 + sk.length + 1 + id.length + 1 + pw.length];
        int pos = 0;
        msg[pos++] = (byte) sk.length; System.arraycopy(sk, 0, msg, pos, sk.length); pos += sk.length;
        msg[pos++] = (byte) id.length; System.arraycopy(id, 0, msg, pos, id.length); pos += id.length;
        msg[pos++] = (byte) pw.length; System.arraycopy(pw, 0, msg, pos, pw.length);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return HexFormat.of().formatHex(cipher.doFinal(msg));
    }
}
