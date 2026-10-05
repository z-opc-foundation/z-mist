package com.zifang.z.mist.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密钥材料的对称加解密。
 * <p>
 * <b>格式</b>：{@code zm1:} + Base64(nonce[12] ‖ ciphertext‖tag[16])，AES/GCM/NoPadding。
 * nonce 每条随机 ⇒ 同一明文两次加密得到不同密文。
 * <p>
 * <b>兼容</b>：存量数据是 {@code Cipher.getInstance("AES")} 落的，即 AES/ECB/PKCS5Padding。
 * Base64 字母表 {@code A-Za-z0-9+/=} 不含 {@code ':'}，所以 {@code "zm1:"} 前缀不可能与存量密文混淆，
 * 解密时可以无歧义地分流：带前缀走 GCM，不带前缀走 ECB 旧路径。
 * <p>
 * <b>KDF</b>：{@link #deriveKey(String)} 固定走 {@code MD5(passphrase)} 得到 16 字节，
 * 与本仓历史实现逐字节一致 —— 改了它存量密文全部解不开。信封加密的数据密钥是原始 32 字节，
 * 走 {@link #encryptWithKey} / {@link #decryptWithKey} 原样使用，不经过这一步。
 *
 * @author zifang
 * @since 1.0.5
 */
public final class MistCrypto {

    /** 新格式前缀。Base64 不含 ':'，故与存量密文无歧义。 */
    private static final String PREFIX_V1 = "zm1:";

    private static final String TRANSFORM_GCM = "AES/GCM/NoPadding";
    private static final String TRANSFORM_LEGACY = "AES"; // = AES/ECB/PKCS5Padding

    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private MistCrypto() {
    }

    /**
     * 主密钥 → AES 密钥材料。固定 MD5 单趟 16 字节，与历史实现一致（存量密文依赖它）。
     */
    public static byte[] deriveKey(String masterKey) throws Exception {
        return MessageDigest.getInstance("MD5")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 是否为 {@code zm1:} 新格式。false 表示存量 ECB 密文。 */
    public static boolean isLegacy(String storedValue) {
        return storedValue == null || !storedValue.startsWith(PREFIX_V1);
    }

    /** 用主密钥加密，产出 {@code zm1:} 新格式。 */
    public static String encrypt(String plainValue, String masterKey) throws Exception {
        return encryptWithKey(plainValue, deriveKey(masterKey));
    }

    /** 用主密钥解密。带 {@code zm1:} 前缀走 GCM，否则按存量 AES/ECB/PKCS5Padding 解。 */
    public static String decrypt(String storedValue, String masterKey) throws Exception {
        return decryptWithKey(storedValue, deriveKey(masterKey));
    }

    /**
     * 用给定的原始密钥材料加密（信封加密的数据密钥走这条，密钥长度原样保留）。
     *
     * @param key 16 / 24 / 32 字节的 AES 密钥
     */
    public static String encryptWithKey(String plainValue, byte[] key) throws Exception {
        byte[] nonce = new byte[NONCE_LEN];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance(TRANSFORM_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] sealed = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));

        byte[] blob = new byte[nonce.length + sealed.length];
        System.arraycopy(nonce, 0, blob, 0, nonce.length);
        System.arraycopy(sealed, 0, blob, nonce.length, sealed.length);
        return PREFIX_V1 + Base64.getEncoder().encodeToString(blob);
    }

    /** 用给定的原始密钥材料解密，同时兼容存量 ECB 密文。 */
    public static String decryptWithKey(String storedValue, byte[] key) throws Exception {
        if (isLegacy(storedValue)) {
            // 存量路径：与历史实现逐字节一致（AES/ECB/PKCS5Padding）
            Cipher cipher = Cipher.getInstance(TRANSFORM_LEGACY);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(storedValue));
            return new String(plain, StandardCharsets.UTF_8);
        }

        String body = storedValue.substring(PREFIX_V1.length());
        byte[] blob = Base64.getDecoder().decode(body);
        if (blob.length < NONCE_LEN + (TAG_BITS / 8)) {
            throw new IllegalArgumentException("malformed " + PREFIX_V1 + " payload: " + blob.length + " bytes");
        }
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(blob, 0, nonce, 0, NONCE_LEN);
        byte[] sealed = new byte[blob.length - NONCE_LEN];
        System.arraycopy(blob, NONCE_LEN, sealed, 0, sealed.length);

        Cipher cipher = Cipher.getInstance(TRANSFORM_GCM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] plain = cipher.doFinal(sealed);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
