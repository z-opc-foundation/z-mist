package com.zifang.z.mist.common.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MistCrypto 三代密文格式 / KDF 升级 / 信封加密不变性 的回归闸。
 * <p>
 * 全部凭据均为测试专用哑值，不含任何真实密钥或口令。
 */
class MistCryptoTest {

    // 测试专用哑值（非真实凭据）
    private static final String KEY_A = "unit-test-dummy-master-A-DO-NOT-USE";
    private static final String KEY_B = "unit-test-dummy-master-B-DO-NOT-USE";
    private static final String PLAIN = "dummy-plain-value-0123456789";

    private static final int SALT_LEN = 16;
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;

    /** 复刻旧 KDF：MD5(masterKey) 单趟 16 字节。 */
    private static byte[] md5Key(String masterKey) throws Exception {
        return MessageDigest.getInstance("MD5")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 原始 32 字节数据密钥（测试专用哑值，非真实密钥）。 */
    private static byte[] rawDataKey() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x37);
        return key;
    }

    /** 构造无前缀存量 ECB 密文（升级前最老的一代）。 */
    private static String legacyEcb(String plain, byte[] key) throws Exception {
        Cipher c = Cipher.getInstance("AES"); // = AES/ECB/PKCS5Padding
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return Base64.getEncoder().encodeToString(
                c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    /** 用固定 nonce 手工构造 zm1: 密文（模拟升级前落库的存量，布局不依赖随机）。 */
    private static String handcraftedV1(String plain, byte[] key) throws Exception {
        byte[] nonce = new byte[NONCE_LEN]; // 测试用固定 nonce，仅用于构造存量样本
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_LEN * 8, nonce));
        byte[] sealed = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] blob = new byte[nonce.length + sealed.length];
        System.arraycopy(nonce, 0, blob, 0, nonce.length);
        System.arraycopy(sealed, 0, blob, nonce.length, sealed.length);
        return "zm1:" + Base64.getEncoder().encodeToString(blob);
    }

    /** 剥掉 zm2: 前缀解 Base64，得到 [salt|nonce|ct|tag]。 */
    private static byte[] v2Blob(String stored) {
        assertTrue(stored.startsWith("zm2:"), "应为 zm2: 格式, 实际: " + stored);
        return Base64.getDecoder().decode(stored.substring("zm2:".length()));
    }

    private static String v2Of(byte[] blob) {
        return "zm2:" + Base64.getEncoder().encodeToString(blob);
    }

    // ---------------------------------------------------------------- 新格式

    @Test
    void v2RoundTripAndPrefix() throws Exception {
        String stored = MistCrypto.encrypt(PLAIN, KEY_A);
        assertTrue(stored.startsWith("zm2:"), "新写入应产出 zm2: 格式");
        assertFalse(MistCrypto.isLegacy(stored), "zm2: 不是存量格式");
        assertEquals(PLAIN, MistCrypto.decrypt(stored, KEY_A));
        // 布局自含 salt：salt[16] + nonce[12] + ct + tag[16]
        assertEquals(SALT_LEN + NONCE_LEN + PLAIN.getBytes(StandardCharsets.UTF_8).length + TAG_LEN,
                v2Blob(stored).length);
    }

    @Test
    void v2SaltAndNonceAreRandomPerEncryption() throws Exception {
        String c1 = MistCrypto.encrypt(PLAIN, KEY_A);
        String c2 = MistCrypto.encrypt(PLAIN, KEY_A);
        assertNotEquals(c1, c2, "同明文两次加密必须得到不同密文");

        byte[] b1 = v2Blob(c1);
        byte[] b2 = v2Blob(c2);
        assertFalse(Arrays.equals(Arrays.copyOfRange(b1, 0, SALT_LEN),
                        Arrays.copyOfRange(b2, 0, SALT_LEN)),
                "每次加密的 salt 必须不同");
        assertFalse(Arrays.equals(Arrays.copyOfRange(b1, SALT_LEN, SALT_LEN + NONCE_LEN),
                        Arrays.copyOfRange(b2, SALT_LEN, SALT_LEN + NONCE_LEN)),
                "每次加密的 nonce 必须不同");
        // 两次都能各自还原（salt 自含，无需外部状态）
        assertEquals(PLAIN, MistCrypto.decrypt(c1, KEY_A));
        assertEquals(PLAIN, MistCrypto.decrypt(c2, KEY_A));
    }

    // ---------------------------------------------------------------- 存量两代仍可读

    @Test
    void v1LegacyCiphertextStillDecrypts() throws Exception {
        // 升级前 encrypt() = encryptWithKey(plain, MD5(masterKey))，产出 zm1:
        byte[] legacyKey = md5Key(KEY_A);
        String v1 = MistCrypto.encryptWithKey(PLAIN, legacyKey);
        assertTrue(v1.startsWith("zm1:"));
        assertFalse(MistCrypto.isLegacy(v1));
        assertEquals(PLAIN, MistCrypto.decrypt(v1, KEY_A), "zm1: 存量必须仍走旧 MD5 派生且可读");
    }

    @Test
    void ecbLegacyCiphertextStillDecrypts() throws Exception {
        String ecb = legacyEcb(PLAIN, md5Key(KEY_A));
        assertTrue(MistCrypto.isLegacy(ecb), "无前缀密文应识别为存量");
        assertEquals(PLAIN, MistCrypto.decrypt(ecb, KEY_A), "无前缀 ECB 存量必须仍可读");
    }

    // ---------------------------------------------------------------- 失败语义

    @Test
    void wrongKeyFailsForAllThreeGenerations() throws Exception {
        String v2 = MistCrypto.encrypt(PLAIN, KEY_A);
        String v1 = MistCrypto.encryptWithKey(PLAIN, md5Key(KEY_A));
        String ecb = legacyEcb(PLAIN, md5Key(KEY_A));

        // GCM 两代带认证 tag：错误密钥必然抛异常，绝不静默返回明文/空串
        assertThrows(Exception.class, () -> MistCrypto.decrypt(v2, KEY_B));
        assertThrows(Exception.class, () -> MistCrypto.decrypt(v1, KEY_B));

        // 存量 ECB 无认证：错误密钥可能抛 BadPaddingException，也可能产出乱码；
        // 底线是绝不还原出正确明文，也绝不静默返回空串
        String got;
        try {
            got = MistCrypto.decrypt(ecb, KEY_B);
        } catch (Exception e) {
            got = "<decryption-failed>";
        }
        assertNotEquals(PLAIN, got, "错误密钥绝不能还原出明文");
        assertNotEquals("", got, "错误密钥绝不能静默返回空串");
    }

    @Test
    void tamperedV2CiphertextOrTagFails() throws Exception {
        String stored = MistCrypto.encrypt(PLAIN, KEY_A);

        byte[] ct = v2Blob(stored);
        ct[SALT_LEN + NONCE_LEN] ^= 0x01; // 篡改 ciphertext 首字节
        assertThrows(Exception.class, () -> MistCrypto.decrypt(v2Of(ct), KEY_A));

        byte[] tag = v2Blob(stored);
        tag[tag.length - 1] ^= 0x01; // 篡改 GCM tag
        assertThrows(Exception.class, () -> MistCrypto.decrypt(v2Of(tag), KEY_A));
    }

    @Test
    void malformedV2PayloadFails() {
        assertThrows(Exception.class, () -> MistCrypto.decrypt("zm2:", KEY_A)); // 空 body
        assertThrows(Exception.class,
                () -> MistCrypto.decrypt("zm2:" + Base64.getEncoder().encodeToString(new byte[8]), KEY_A)); // 过短
        assertThrows(Exception.class, () -> MistCrypto.decrypt("zm2:!!!not-base64!!!", KEY_A)); // 非法 Base64
    }

    @Test
    void wrongPrefixOrEmptyFails() {
        assertThrows(Exception.class, () -> MistCrypto.decrypt("zm3:AAAA", KEY_A)); // 未知版本前缀
        assertThrows(Exception.class, () -> MistCrypto.decrypt("zm9:whatever", KEY_A));
        assertThrows(Exception.class, () -> MistCrypto.decrypt((String) null, KEY_A));
        assertThrows(Exception.class, () -> MistCrypto.decrypt("", KEY_A));
    }

    // ---------------------------------------------------------------- 信封加密：行为完全不变

    @Test
    void envelopeEncryptWithKeyStillProducesV1Layout() throws Exception {
        byte[] rawKey = rawDataKey();
        String out = MistCrypto.encryptWithKey(PLAIN, rawKey);

        assertTrue(out.startsWith("zm1:"), "信封路径仍产出 zm1:, 不受 KDF 升级影响");
        assertFalse(out.startsWith("zm2:"));
        // 旧布局：nonce[12] + ct + tag[16]，没有 salt —— 精确长度证明布局未变
        byte[] blob = Base64.getDecoder().decode(out.substring("zm1:".length()));
        assertEquals(NONCE_LEN + PLAIN.getBytes(StandardCharsets.UTF_8).length + TAG_LEN, blob.length);
        assertEquals(PLAIN, MistCrypto.decryptWithKey(out, rawKey));
    }

    @Test
    void envelopeDecryptWithKeyHandlesHandcraftedV1AndEcb() throws Exception {
        byte[] rawKey = rawDataKey();
        // 手工构造的 zm1:（升级前落库样本）仍可解
        assertEquals(PLAIN, MistCrypto.decryptWithKey(handcraftedV1(PLAIN, rawKey), rawKey));
        // 无前缀 ECB 存量仍可解
        assertEquals(PLAIN, MistCrypto.decryptWithKey(legacyEcb(PLAIN, rawKey), rawKey));
    }

    @Test
    void masterKeyPathAndEnvelopePathDoNotCross() throws Exception {
        byte[] rawKey = rawDataKey();
        String v2 = MistCrypto.encrypt(PLAIN, KEY_A);          // 主密钥路径 → zm2:
        String env = MistCrypto.encryptWithKey(PLAIN, rawKey);  // 信封路径   → zm1:

        assertTrue(v2.startsWith("zm2:"));
        assertTrue(env.startsWith("zm1:"));
        assertEquals(PLAIN, MistCrypto.decrypt(v2, KEY_A));
        assertEquals(PLAIN, MistCrypto.decryptWithKey(env, rawKey));
        // 信封密文用原始密钥加密，主密钥路径按 zm1: 用 MD5 派生去解必然失败 —— 两条密钥空间互不串味
        assertThrows(Exception.class, () -> MistCrypto.decrypt(env, KEY_A));
    }
}
