package com.zifang.z.mist.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密钥材料的对称加解密（三代密文格式并存，写入只产最新一代）。
 *
 * <p><b>格式与分流</b>（按前缀，Base64 字母表 {@code A-Za-z0-9+/=} 不含 {@code ':'}，故前缀无歧义）：
 * <ul>
 *   <li>{@code zm2:} + Base64(salt[16] ‖ nonce[12] ‖ ciphertext‖tag[16]) —— <b>当前写入格式</b>。
 *       AES/GCM/NoPadding，密钥由 {@code PBKDF2WithHmacSHA256(masterKey, salt, 120000, 256bit)} 派生。
 *       每条随机 salt + 随机 nonce ⇒ 同明文两次密文不同；salt 自含于密文 ⇒ 解密无需外部状态。</li>
 *   <li>{@code zm1:} + Base64(nonce[12] ‖ ciphertext‖tag[16]) —— <b>存量只读</b>。
 *       AES/GCM/NoPadding，密钥为 {@code MD5(masterKey)} 单趟 16 字节（旧 KDF）。</li>
 *   <li>无前缀 —— <b>存量只读</b>。{@code Cipher.getInstance("AES")} = AES/ECB/PKCS5Padding，
 *       密钥为 {@code MD5(masterKey)} 单趟 16 字节（旧 KDF），确定性加密。</li>
 * </ul>
 *
 * <p><b>为什么不破坏存量</b>：旧 KDF（MD5 单趟）只保留给 {@code zm1:} / 无前缀两条解密路径使用，
 * 与新写入路径彻底隔离；新格式 {@code zm2:} 自带 salt，解密时按前缀各走各的派生函数。
 * 因此「把 KDF 换掉存量就解不开」的旧约束不再成立：存量一条都不用迁移，
 * 需要更强保护时按需读出明文再用 {@link #encrypt} 重加密即可。
 *
 * <p><b>KDF 演进</b>：{@code encrypt}/{@code decrypt}（主密钥路径）新写入改用 PBKDF2-HMAC-SHA256
 * （带随机 salt、120000 迭代、256 位密钥），抵御弱口令离线爆破；旧 MD5 单趟派生降级为私有的
 * {@code deriveLegacyKey}，仅服务存量解密，不被新写入路径调用。本类已发布 Maven Central，
 * 为不破坏下游二进制兼容，另保留一个 {@code @Deprecated deriveKey} 委托入口（同样禁止用于新写入）。
 *
 * <p><b>信封加密（不受影响）</b>：{@link #encryptWithKey} / {@link #decryptWithKey} 走原始
 * 16/24/32 字节数据密钥（AES/GCM，产出 {@code zm1:}），不经过任何 KDF，
 * 行为保持历史实现逐字节不变；本次改动只动 {@code encrypt}/{@code decrypt} 主密钥路径。
 *
 * <p><b>性能</b>：{@code zm2:} 每次加密/解密多一次 PBKDF2（约十几万次 HMAC，单次几十毫秒量级），
 * 相较旧的 MD5 单趟明显更重；这是用计算成本换离线爆破难度，属预期代价。
 * 存量 {@code zm1:}/ECB 解密仍走 MD5，零额外开销。
 *
 * <p><b>失败语义</b>：解密失败（错误密钥、密文/tag 被篡改、未知前缀、畸形 payload）一律抛异常，
 * 绝不静默返回明文或空串。
 *
 * @author zifang
 * @since 1.0.5
 */
public final class MistCrypto {

    /** 存量 GCM 格式前缀（只读）。Base64 不含 ':'，故与存量密文无歧义。 */
    private static final String PREFIX_V1 = "zm1:";
    /** 当前写入格式前缀：PBKDF2 + AES-GCM，密文自含 salt。 */
    private static final String PREFIX_V2 = "zm2:";

    private static final String TRANSFORM_GCM = "AES/GCM/NoPadding";
    private static final String TRANSFORM_LEGACY = "AES"; // = AES/ECB/PKCS5Padding

    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int SALT_LEN = 16;
    /** PBKDF2 迭代次数：120000，兼顾离线爆破成本与测试耗时。 */
    private static final int PBKDF2_ITERATIONS = 120_000;
    /** 派生 256 位 AES 密钥（32 字节）。 */
    private static final int DERIVED_KEY_BITS = 256;
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final SecureRandom RANDOM = new SecureRandom();

    private MistCrypto() {
    }

    /**
     * 旧 KDF：{@code MD5(masterKey)} 单趟 16 字节，与历史实现逐字节一致。
     * <b>仅供 {@code zm1:} / 无前缀存量密文解密使用</b>，新写入路径不得调用。
     * 实现本身保持私有，外部只能通过下方 {@code @Deprecated deriveKey} 兼容入口访问。
     */
    private static byte[] deriveLegacyKey(String masterKey) throws Exception {
        return MessageDigest.getInstance("MD5")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 旧 KDF 的兼容入口 —— <b>仅为已发布构件的二进制兼容而保留</b>。
     *
     * <p>返回 {@code MD5(masterKey)} 单趟 16 字节，与 1.0.5 以来的历史实现逐字节一致。
     * <b>禁止用它加密新数据</b>：新写入一律走 {@link #encrypt}（{@code zm2:} + PBKDF2）。
     * 它只应用于解密/校验 {@code zm1:} 或无前缀的存量密文。</p>
     *
     * @deprecated 弱 KDF（无 salt、单趟 MD5），仅存量兼容；新代码请用 {@link #encrypt} / {@link #decrypt}。
     */
    @Deprecated
    public static byte[] deriveKey(String masterKey) throws Exception {
        return deriveLegacyKey(masterKey);
    }

    /** 新 KDF：PBKDF2-HMAC-SHA256(masterKey, salt) 派生 32 字节 AES 密钥。 */
    private static byte[] deriveStrongKey(String masterKey, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                masterKey.toCharArray(), salt, PBKDF2_ITERATIONS, DERIVED_KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM)
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * 是否为存量/未知格式：不带任何已知版本前缀（{@code zm1:} / {@code zm2:}）即 true。
     * <p>注意：{@link #decryptWithKey} 不依赖本方法（其内部内联判断以保持历史行为）；
     * 本方法供写入侧/运维判定使用。
     */
    public static boolean isLegacy(String storedValue) {
        return storedValue == null
                || !(storedValue.startsWith(PREFIX_V1) || storedValue.startsWith(PREFIX_V2));
    }

    /** 用主密钥加密，产出 {@code zm2:} 新格式（PBKDF2 + 随机 salt/nonce + AES-GCM）。 */
    public static String encrypt(String plainValue, String masterKey) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        byte[] nonce = new byte[NONCE_LEN];
        RANDOM.nextBytes(nonce);
        byte[] key = deriveStrongKey(masterKey, salt);

        Cipher cipher = Cipher.getInstance(TRANSFORM_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] sealed = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));

        byte[] blob = new byte[salt.length + nonce.length + sealed.length];
        System.arraycopy(salt, 0, blob, 0, salt.length);
        System.arraycopy(nonce, 0, blob, salt.length, nonce.length);
        System.arraycopy(sealed, 0, blob, salt.length + nonce.length, sealed.length);
        return PREFIX_V2 + Base64.getEncoder().encodeToString(blob);
    }

    /**
     * 用主密钥解密，按前缀三路分流：
     * {@code zm2:} → PBKDF2+AES-GCM；{@code zm1:} → 旧 MD5+AES-GCM（存量）；
     * 无前缀 → 旧 MD5+AES/ECB（存量）。未知前缀 / 畸形 payload 一律抛异常。
     */
    public static String decrypt(String storedValue, String masterKey) throws Exception {
        if (storedValue == null || storedValue.isEmpty()) {
            throw new IllegalArgumentException("ciphertext is null or empty");
        }
        if (storedValue.startsWith(PREFIX_V2)) {
            return decryptV2(storedValue, masterKey);
        }
        if (storedValue.startsWith(PREFIX_V1)) {
            return decryptWithKey(storedValue, deriveLegacyKey(masterKey));
        }
        // 存量 ECB 密文是纯 Base64（不含 ':'）；含 ':' 又不是已知前缀 ⇒ 未知版本/畸形
        if (storedValue.indexOf(':') >= 0) {
            throw new IllegalArgumentException("unsupported ciphertext format prefix");
        }
        return decryptWithKey(storedValue, deriveLegacyKey(masterKey));
    }

    /** {@code zm2:} 解密：从密文自含的 salt 派生密钥，再按 nonce 走 AES-GCM。 */
    private static String decryptV2(String storedValue, String masterKey) throws Exception {
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(storedValue.substring(PREFIX_V2.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed " + PREFIX_V2 + " payload: invalid base64", e);
        }
        int minLen = SALT_LEN + NONCE_LEN + (TAG_BITS / 8);
        if (blob.length < minLen) {
            throw new IllegalArgumentException(
                    "malformed " + PREFIX_V2 + " payload: " + blob.length + " bytes");
        }
        byte[] salt = new byte[SALT_LEN];
        byte[] nonce = new byte[NONCE_LEN];
        byte[] sealed = new byte[blob.length - SALT_LEN - NONCE_LEN];
        System.arraycopy(blob, 0, salt, 0, SALT_LEN);
        System.arraycopy(blob, SALT_LEN, nonce, 0, NONCE_LEN);
        System.arraycopy(blob, SALT_LEN + NONCE_LEN, sealed, 0, sealed.length);

        byte[] key = deriveStrongKey(masterKey, salt);
        Cipher cipher = Cipher.getInstance(TRANSFORM_GCM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] plain = cipher.doFinal(sealed); // tag 校验失败抛 AEADBadTagException
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 用给定的原始密钥材料加密（信封加密的数据密钥走这条，密钥长度原样保留）。
     * <b>本方法与 {@link #decryptWithKey} 不经过任何 KDF，行为保持历史实现不变</b>，
     * 产出 {@code zm1:} 格式。
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

    /** 用给定的原始密钥材料解密，同时兼容存量 ECB 密文。行为保持历史实现不变。 */
    public static String decryptWithKey(String storedValue, byte[] key) throws Exception {
        // 内联旧 isLegacy 判断（等价于 storedValue==null || !startsWith("zm1:")），
        // 不依赖 isLegacy() 的语义演进，保持本方法对任意输入的历史行为逐字节不变。
        if (storedValue == null || !storedValue.startsWith(PREFIX_V1)) {
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
