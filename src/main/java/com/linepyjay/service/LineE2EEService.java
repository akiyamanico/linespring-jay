package com.linepyjay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class LineE2EEService {

    private static final Logger log = LoggerFactory.getLogger(LineE2EEService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> storage = new HashMap<>();

    public void storageSet(String key, String value) { storage.put(key, value); }
    public String storageGet(String key)             { return storage.get(key); }


    public void saveE2EESelfKeyData(String mid, int keyId,
                                    byte[] privKey, byte[] pubKey,
                                    int e2eeVersion) throws Exception {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("keyId",       keyId);
        d.put("privKey",     Base64.getEncoder().encodeToString(privKey));
        d.put("pubKey",      Base64.getEncoder().encodeToString(pubKey));
        d.put("e2eeVersion", e2eeVersion);
        String json = MAPPER.writeValueAsString(d);
        storageSet("e2eeKeys:" + mid,   json);
        storageSet("e2eeKeys:" + keyId, json);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getE2EESelfKeyData(String mid) throws Exception {
        String j = storageGet("e2eeKeys:" + mid);
        if (j == null) throw new RuntimeException("NoE2EEKey: not found for mid=" + mid);
        return MAPPER.readValue(j, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getE2EESelfKeyDataByKeyId(int keyId) throws Exception {
        String j = storageGet("e2eeKeys:" + keyId);
        return j == null ? null : MAPPER.readValue(j, Map.class);
    }

    public byte[] generateSharedSecret(byte[] privateKey, byte[] publicKey) {
        log.debug("generateSharedSecret privLen={} pubLen={}", privateKey.length, publicKey.length);
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(privateKey, 0);
        X25519PublicKeyParameters  pub  = new X25519PublicKeyParameters(publicKey, 0);
        X25519Agreement ag = new X25519Agreement();
        ag.init(priv);
        byte[] secret = new byte[32];
        ag.calculateAgreement(pub, secret, 0);
        return secret;
    }

    public byte[] getSHA256Sum(Object... args) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Object arg : args) {
            if      (arg instanceof byte[]) md.update((byte[]) arg);
            else if (arg instanceof String) md.update(((String) arg).getBytes(StandardCharsets.UTF_8));
            else throw new IllegalArgumentException("Unsupported arg: " + arg.getClass());
        }
        return md.digest();
    }
    public byte[] xor(byte[] buf) {
        int half = buf.length / 2;
        byte[] out = new byte[half];
        for (int i = 0; i < half; i++) out[i] = (byte) (buf[i] ^ buf[half + i]);
        return out;
    }

    public byte[] encryptAESECB(byte[] aesKey, byte[] plainData) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        return c.doFinal(plainData);
    }


    private byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] data, boolean pkcs5) throws Exception {
        Cipher c = Cipher.getInstance(pkcs5 ? "AES/CBC/PKCS5Padding" : "AES/CBC/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return c.doFinal(data);
    }

    private byte[] aesCbcEncrypt(byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return c.doFinal(data);
    }

    public byte[][] decryptKeyChain(byte[] publicKey, byte[] privateKey,
                                    byte[] encryptedKeyChain) throws Exception {
        log.debug("decryptKeyChainKeyInfo pub={} priv={} chain={}",
                b64(publicKey), b64(privateKey), b64(encryptedKeyChain));

        byte[] sharedSecret = generateSharedSecret(privateKey, publicKey);
        byte[] aesKey = getSHA256Sum(sharedSecret, "Key");
        byte[] aesIv  = xor(getSHA256Sum(sharedSecret, "IV"));

        byte[] keychainData = aesCbcDecrypt(aesKey, aesIv, encryptedKeyChain, false);
        log.debug("decryptKeyChainBinKeyInfo hex={}", HexFormat.of().formatHex(keychainData));

        byte[] privKeyBytes = readThriftBinaryField(keychainData, (short) 5);
        byte[] pubKeyBytes  = readThriftBinaryField(keychainData, (short) 4);
        return new byte[][]{ privKeyBytes, pubKeyBytes };
    }

    public byte[] encryptDeviceSecret(byte[] publicKey, byte[] privateKey,
                                      byte[] encryptedKeyChain) throws Exception {
        byte[] sharedSecret = generateSharedSecret(privateKey, publicKey);
        byte[] aesKey = getSHA256Sum(sharedSecret, "Key");
        encryptedKeyChain = xor(getSHA256Sum(encryptedKeyChain));
        return encryptAESECB(aesKey, encryptedKeyChain);
    }

    /**
     * mirrors: decodeE2EEKeyV1(data, secret)
     *
     * @param encryptedKeyChainB64  data.encryptedKeyChain
     * @param keyId                 data.keyId
     * @param publicKeyB64          data.publicKey  (server ephemeral pubkey)
     * @param e2eeVersion           data.e2eeVersion
     * @param secret                our login secret (private key from createSqrSecret)
     */
    public Map<String, Object> decodeE2EEKeyV1(String encryptedKeyChainB64, int keyId,
                                               String publicKeyB64, int e2eeVersion,
                                               byte[] secret) throws Exception {
        byte[] encryptedKeyChain = Base64.getDecoder().decode(encryptedKeyChainB64);
        byte[] publicKey         = Base64.getDecoder().decode(publicKeyB64);

        byte[][] keys = decryptKeyChain(publicKey, secret, encryptedKeyChain);
        byte[] privKey = keys[0];
        byte[] pubKey  = keys[1];

        log.debug("decodeE2EEKeyV1 keyId={} e2eeVersion={}", keyId, e2eeVersion);

        Map<String, Object> kd = new LinkedHashMap<>();
        kd.put("keyId",       keyId);
        kd.put("privKey",     b64(privKey));
        kd.put("pubKey",      b64(pubKey));
        kd.put("e2eeVersion", e2eeVersion);
        storageSet("e2eeKeys:" + keyId, MAPPER.writeValueAsString(kd));
        return kd;
    }

    public Object[] createSqrSecret(boolean base64Only) {
        byte[] secretKey = new byte[32];
        RNG.nextBytes(secretKey);
        // Clamp per Curve25519 spec
        secretKey[0]  &= 248;
        secretKey[31] &= 127;
        secretKey[31] |= 64;

        byte[] publicKey = new X25519PrivateKeyParameters(secretKey, 0)
                .generatePublicKey().getEncoded();
        String pubKeyB64 = b64(publicKey);

        if (base64Only) return new Object[]{ secretKey, pubKeyB64 };

        String encoded = pubKeyB64
                .replace("+", "%2B")
                .replace("/", "%2F")
                .replace("=", "%3D");
        return new Object[]{ secretKey, "?secret=" + encoded + "&e2eeVersion=1" };
    }

    public byte[] getIntBytes(int i) {
        return ByteBuffer.allocate(4).putInt(i).array();
    }

    public byte[] generateAAD(String to, String from,
                               int senderKeyId, int receiverKeyId,
                               int specVersion, int contentType) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(to.getBytes(StandardCharsets.UTF_8));
        buf.write(from.getBytes(StandardCharsets.UTF_8));
        buf.write(getIntBytes(senderKeyId));
        buf.write(getIntBytes(receiverKeyId));
        buf.write(getIntBytes(specVersion));
        buf.write(getIntBytes(contentType));
        return buf.toByteArray();
    }

    public byte[] encryptE2EEMessageV2(byte[] data, byte[] gcmKey,
                                        byte[] nonce, byte[] aad) throws Exception {
        log.debug("createCipheriv data={} gcmKey={} nonce={} aad={}",
                data.length, gcmKey.length, nonce.length, aad.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(gcmKey, "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(data); 
    }

    /**
     *
     *   chunks[0] salt          16 bytes random
     *   chunks[1] message       ciphertext[0..-16] || authTag[-16..]
     *   chunks[2] sign/nonce    12 bytes (GCM IV)
     *   chunks[3] senderKeyId   4 bytes big-endian
     *   chunks[4] receiverKeyId 4 bytes big-endian
     */
    public Map<String, Object> decryptE2EEMessageV2(
            String to, String from,
            byte[][] chunks,
            byte[] privK, byte[] pubK,
            int specVersion, int contentType) throws Exception {

        byte[] salt       = chunks[0];
        byte[] message    = chunks[1];
        byte[] ciphertext = Arrays.copyOfRange(message, 0, message.length - 16);
        byte[] tag        = Arrays.copyOfRange(message, message.length - 16, message.length);
        byte[] sign       = chunks[2]; // GCM nonce
        int senderKeyId   = byte2int(chunks[3]);
        int receiverKeyId = byte2int(chunks[4]);

        byte[] aesKey = generateSharedSecret(privK, pubK);
        byte[] gcmKey = getSHA256Sum(aesKey, salt, "Key");
        byte[] aad    = generateAAD(to, from, senderKeyId, receiverKeyId, specVersion, contentType);

        log.debug("decryptE2EEMessageV2Info iv={} key={} tag={} aad={} enc={}",
                sign.length, gcmKey.length, tag.length, aad.length, ciphertext.length);

        byte[] ctWithTag = concat(ciphertext, tag);

        byte[] decrypted;
        try {
            decrypted = gcmDecrypt(gcmKey, sign, aad, ctWithTag);
        } catch (Exception e) {
            log.debug("decryptE2EEMessageV2DecryptionFailed {}, retrying...", e.getMessage());
            decrypted = gcmDecrypt(gcmKey, sign, aad, ctWithTag); 
        }

        log.debug("decryptE2EEMessageV2DecryptedMessage {}",
                new String(decrypted, StandardCharsets.UTF_8));
        return MAPPER.readValue(decrypted, Map.class);
    }

    private byte[] gcmDecrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ctWithTag) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        c.updateAAD(aad);
        return c.doFinal(ctWithTag);
    }

    public Map<String, Object> decryptE2EEMessageV1(
            byte[][] chunks, byte[] privK, byte[] pubK) throws Exception {
        log.debug("decryptE2EEMessageV1_arg privK={} pubK={}", privK.length, pubK.length);

        byte[] salt    = chunks[0];
        byte[] message = chunks[1];

        byte[] aesKey  = generateSharedSecret(privK, pubK);
        byte[] aes_key = getSHA256Sum(aesKey, salt, "Key");
        byte[] aes_iv  = xor(getSHA256Sum(aesKey, salt, "IV"));

        byte[] decrypted;
        try {
            decrypted = aesCbcDecrypt(aes_key, aes_iv, message, true);
        } catch (Exception e) {
            decrypted = aesCbcDecrypt(aes_key, aes_iv, message, false); 
        }

        log.debug("decryptE2EEMessageV1DecryptedMessage {}",
                new String(decrypted, StandardCharsets.UTF_8));
        return MAPPER.readValue(decrypted, Map.class);
    }

    public byte[][] encryptE2EETextMessage(
            int senderKeyId, int receiverKeyId,
            byte[] keyData, int specVersion,
            String text, String to, String from) throws Exception {

        byte[] salt    = new byte[16]; RNG.nextBytes(salt);
        byte[] gcmKey  = getSHA256Sum(keyData, salt, "Key");
        byte[] aad     = generateAAD(to, from, senderKeyId, receiverKeyId, specVersion, 0);
        byte[] sign    = new byte[12]; RNG.nextBytes(sign);
        byte[] data    = MAPPER.writeValueAsString(Map.of("text", text))
                               .getBytes(StandardCharsets.UTF_8);
        byte[] encData = encryptE2EEMessageV2(data, gcmKey, sign, aad);

        byte[] bSender   = getIntBytes(senderKeyId);
        byte[] bReceiver = getIntBytes(receiverKeyId);
        log.debug("encryptE2EETextMessageSenderKeyId {} ({})", senderKeyId, HexFormat.of().formatHex(bSender));
        log.debug("encryptE2EETextMessageReceiverKeyId {} ({})", receiverKeyId, HexFormat.of().formatHex(bReceiver));

        return new byte[][]{ salt, encData, sign, bSender, bReceiver };
    }

    public byte[][] encryptE2EELocationMessage(
            int senderKeyId, int receiverKeyId,
            byte[] keyData, int specVersion,
            Map<String, Object> location, String to, String from) throws Exception {

        byte[] salt    = new byte[16]; RNG.nextBytes(salt);
        byte[] gcmKey  = getSHA256Sum(keyData, salt, "Key");
        byte[] aad     = generateAAD(to, from, senderKeyId, receiverKeyId, specVersion, 15);
        byte[] sign    = new byte[12]; RNG.nextBytes(sign);
        byte[] data    = MAPPER.writeValueAsString(Map.of("location", location))
                               .getBytes(StandardCharsets.UTF_8);
        byte[] encData = encryptE2EEMessageV2(data, gcmKey, sign, aad);

        return new byte[][]{ salt, encData, sign, getIntBytes(senderKeyId), getIntBytes(receiverKeyId) };
    }

    public byte[][] encryptE2EEMessageByData(
            int senderKeyId, int receiverKeyId,
            byte[] keyData, int specVersion,
            Map<String, Object> rawData, String to, String from,
            int contentType) throws Exception {

        byte[] salt    = new byte[16]; RNG.nextBytes(salt);
        byte[] gcmKey  = getSHA256Sum(keyData, salt, "Key");
        byte[] aad     = generateAAD(to, from, senderKeyId, receiverKeyId, specVersion, contentType);
        byte[] sign    = new byte[12]; RNG.nextBytes(sign);
        byte[] data    = MAPPER.writeValueAsString(rawData).getBytes(StandardCharsets.UTF_8);
        byte[] encData = encryptE2EEMessageV2(data, gcmKey, sign, aad);

        return new byte[][]{ salt, encData, sign, getIntBytes(senderKeyId), getIntBytes(receiverKeyId) };
    }

    public byte[] decryptGroupSharedKey(byte[] selfPrivKey, byte[] creatorPublicKey,
                                         byte[] encryptedSharedKey) throws Exception {
        byte[] aesKey  = generateSharedSecret(selfPrivKey, creatorPublicKey);
        byte[] aes_key = getSHA256Sum(aesKey, "Key");
        byte[] aes_iv  = xor(getSHA256Sum(aesKey, "IV"));

        log.debug("getE2EELocalPublicKeyAESInfo aes_key={} aes_iv={} encLen={}",
                aes_key.length, aes_iv.length, encryptedSharedKey.length);

        byte[] plain;
        try {
            plain = aesCbcDecrypt(aes_key, aes_iv, encryptedSharedKey, true);
        } catch (Exception e) {
            plain = aesCbcDecrypt(aes_key, aes_iv, encryptedSharedKey, false);
        }
        log.debug("getE2EELocalPublicKeyDecryptedLength {}", plain.length);
        return plain;
    }

    public byte[] encryptGroupSharedKey(byte[] selfPrivKey, byte[] memberPublicKey,
                                         byte[] sharedPrivKey) throws Exception {
        byte[] aesKey  = generateSharedSecret(selfPrivKey, memberPublicKey);
        byte[] aes_key = getSHA256Sum(aesKey, "Key");
        byte[] aes_iv  = xor(getSHA256Sum(aesKey, "IV"));
        return aesCbcEncrypt(aes_key, aes_iv, sharedPrivKey);
    }


    public record FileKeys(byte[] encKey, byte[] macKey, byte[] nonce) {}
    public record EncryptedFile(String keyMaterial, byte[] encryptedData) {}

    /**
     * mirrors: deriveKeyMaterial(keyMaterial)
     * HKDF-SHA256, salt=empty, info="FileEncryption", length=76
     * encKey = [0..32], macKey = [32..64], nonce = [64..76] + 4 zero bytes
     */
    public FileKeys deriveKeyMaterial(byte[] keyMaterial) throws Exception {
        byte[] derived = hkdfSha256(keyMaterial, new byte[0], "FileEncryption", 76);
        byte[] encKey  = Arrays.copyOfRange(derived, 0, 32);
        byte[] macKey  = Arrays.copyOfRange(derived, 32, 64);
        byte[] nonce   = new byte[16]; // [64..76] + 4 zeros
        System.arraycopy(derived, 64, nonce, 0, 12);
        return new FileKeys(encKey, macKey, nonce);
    }

    public EncryptedFile encryptByKeyMaterial(byte[] rawData, byte[] keyMaterial) throws Exception {
        if (keyMaterial == null) { keyMaterial = new byte[32]; RNG.nextBytes(keyMaterial); }
        FileKeys keys   = deriveKeyMaterial(keyMaterial);
        byte[]  encData = aesCtrEncrypt(keys.encKey(), keys.nonce(), rawData);
        byte[]  sign    = signData(encData, keys.macKey());
        return new EncryptedFile(b64(keyMaterial), concat(encData, sign));
    }

    public byte[] decryptByKeyMaterial(byte[] rawData, byte[] keyMaterial) throws Exception {
        if (keyMaterial == null) throw new IllegalArgumentException("keyMaterial required");
        FileKeys keys = deriveKeyMaterial(keyMaterial);
        byte[] encData = Arrays.copyOfRange(rawData, 0, rawData.length - 32);
        return aesCtrDecrypt(keys.encKey(), keys.nonce(), encData);
    }

    public byte[] signData(byte[] data, byte[] key) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key, "HmacSHA256"));
        return hmac.doFinal(data);
    }


    private byte[] aesCtrEncrypt(byte[] key, byte[] nonce, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(nonce));
        return c.doFinal(data);
    }

    private byte[] aesCtrDecrypt(byte[] key, byte[] nonce, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(nonce));
        return c.doFinal(data);
    }

    private byte[] hkdfSha256(byte[] ikm, byte[] salt, String info, int length) throws Exception {
        if (salt == null || salt.length == 0) salt = new byte[32];
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = hmac.doFinal(ikm);
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        byte[] okm = new byte[length], t = new byte[0];
        for (int i = 1, pos = 0; pos < length; i++) {
            hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
            hmac.update(t); hmac.update(infoBytes); hmac.update((byte) i);
            t = hmac.doFinal();
            int copy = Math.min(t.length, length - pos);
            System.arraycopy(t, 0, okm, pos, copy);
            pos += copy;
        }
        return okm;
    }


    public int byte2int(byte[] t) {
        int e = 0;
        for (byte b : t) e = 256 * e + (b & 0xFF);
        return e;
    }

    private byte[] readThriftBinaryField(byte[] data, short targetId) {
        try {
            var transport = new org.apache.thrift.transport.TMemoryInputTransport(data);
            var proto     = new org.apache.thrift.protocol.TCompactProtocol(transport);
            return findBinaryField(proto, targetId);
        } catch (Exception e) {
            log.warn("readThriftBinaryField id={} failed: {}", targetId, e.getMessage());
            return new byte[0];
        }
    }

    private byte[] findBinaryField(org.apache.thrift.protocol.TCompactProtocol proto,
                                    short targetId) throws Exception {
        proto.readStructBegin();
        byte[] result = null;
        org.apache.thrift.protocol.TField f;
        while ((f = proto.readFieldBegin()).type != org.apache.thrift.protocol.TType.STOP) {
            if (f.type == org.apache.thrift.protocol.TType.STRUCT) {
                byte[] nested = findBinaryField(proto, targetId);
                if (result == null && nested != null && nested.length > 0) result = nested;
                proto.readStructEnd();
            } else if (f.type == org.apache.thrift.protocol.TType.STRING && f.id == targetId) {
                result = proto.readBinary().array();
            } else {
                proto.skip(f.type);
            }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return result;
    }


    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}