package com.zifang.z.mist.core.domain.service.impl;

import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.common.crypto.MistCrypto;
import com.zifang.z.mist.core.domain.entity.*;
import com.zifang.z.mist.core.domain.mapper.*;
import com.zifang.z.mist.core.support.Proxies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZMistSecretServiceImpl 单元测试(FEATURE026).
 * <p>
 * 覆盖: AES / RSA 加密解密、EaaS、搜索、历史、回滚、轮换、动态密钥。
 * 家法规范 (SOP 步骤 8): JDK 动态代理按方法名分发返回值 + 反射注入,
 * 零 Mockito, 不依赖 Spring/数据库。times()/calls() 替代 verify/captor.
 */
public class ZMistSecretServiceImplTest {

    private ZMistSecretServiceImpl service;

    private Proxies.Builder<ZMistSecretInfoMapper> secretInfo;
    private Proxies.Builder<ZMistSecretHistoryMapper> secretHistory;
    private Proxies.Builder<ZMistSecretAccessLogMapper> secretAccessLog;
    private Proxies.Builder<ZMistSecretDynamicMapper> secretDynamic;
    private Proxies.Builder<ZMistRotationHistoryMapper> rotationHistory;

    @BeforeEach
    void setUp() {
        service = new ZMistSecretServiceImpl();
        secretInfo = Proxies.of(ZMistSecretInfoMapper.class);
        secretHistory = Proxies.of(ZMistSecretHistoryMapper.class);
        secretAccessLog = Proxies.of(ZMistSecretAccessLogMapper.class);
        secretDynamic = Proxies.of(ZMistSecretDynamicMapper.class);
        rotationHistory = Proxies.of(ZMistRotationHistoryMapper.class);

        Proxies.inject(service, "secretInfoMapper", secretInfo.build());
        Proxies.inject(service, "secretHistoryMapper", secretHistory.build());
        Proxies.inject(service, "secretAccessLogMapper", secretAccessLog.build());
        Proxies.inject(service, "secretDynamicMapper", secretDynamic.build());
        Proxies.inject(service, "rotationHistoryMapper", rotationHistory.build());
    }

    // ============ 加密 / 解密 ============

    @Test
    void testAesEncryptDecryptRoundTrip() {
        String plain = "my-super-secret-pwd-12345";
        String cipher = service.encryptValue(plain, Constance.EncryptAlgorithm.AES);
        assertNotNull(cipher);
        assertNotEquals(plain, cipher);
        String decrypted = service.decryptValue(cipher, Constance.EncryptAlgorithm.AES);
        assertEquals(plain, decrypted);
    }

    @Test
    void testRsaEncryptDecryptRoundTrip() {
        String plain = "rsa-test-msg";
        String cipher = service.encryptValue(plain, Constance.EncryptAlgorithm.RSA);
        assertNotNull(cipher);
        String decrypted = service.decryptValue(cipher, Constance.EncryptAlgorithm.RSA);
        assertEquals(plain, decrypted);
    }

    @Test
    void testEaaSEncrypt() {
        String cipher = service.eaaSEncrypt("hello-mist", Constance.EncryptAlgorithm.AES);
        assertNotNull(cipher);
        assertEquals(0, secretInfo.times("insert"), "EaaS 加密不许落库");
    }

    @Test
    void testEaaSDecrypt() {
        String cipher = service.eaaSEncrypt("hello-decrypt", Constance.EncryptAlgorithm.AES);
        String plain = service.eaaSDecrypt(cipher, Constance.EncryptAlgorithm.AES);
        assertEquals("hello-decrypt", plain);
    }

    @Test
    void testEaaSEncryptRsa() {
        String cipher = service.eaaSEncrypt("rsa-eaas", Constance.EncryptAlgorithm.RSA);
        String plain = service.eaaSDecrypt(cipher, Constance.EncryptAlgorithm.RSA);
        assertEquals("rsa-eaas", plain);
    }

    @Test
    void testDecryptToPlainWithExistingCipher() {
        String original = "decrypt-with-explicit-cipher";
        String cipher = service.encryptValue(original, Constance.EncryptAlgorithm.AES);
        String result = service.decryptToPlain("anyKey", "DEFAULT_GROUP", "", cipher);
        assertEquals(original, result);
    }

    @Test
    void testDecryptToPlainFromDb() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setSecretKey("db_key");
        secret.setGroup("DEFAULT_GROUP");
        secret.setNamespace("");
        secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        secret.setEncryptedValue(service.encryptValue("from-db", Constance.EncryptAlgorithm.AES));
        secretInfo.onReturn("selectOne", secret);

        String result = service.decryptToPlain("db_key", "DEFAULT_GROUP", "", null);
        assertEquals("from-db", result);
    }

    // ============ 搜索 ============

    @Test
    void testSearchSecretsWithKeyword() {
        ZMistSecretInfo s1 = new ZMistSecretInfo();
        s1.setSecretKey("db_password");
        ZMistSecretInfo s2 = new ZMistSecretInfo();
        s2.setSecretKey("api_key");
        secretInfo.onReturn("selectList", Arrays.asList(s1, s2));

        List<ZMistSecretInfo> result = service.searchSecrets("db", null, null);
        assertEquals(2, result.size());
        assertEquals(1, secretInfo.times("selectList"));
    }

    @Test
    void testSearchSecretsEmptyKeyword() {
        secretInfo.onReturn("selectList", Collections.emptyList());
        List<ZMistSecretInfo> result = service.searchSecrets("", null, null);
        assertEquals(0, result.size());
    }

    // ============ 历史 & 回滚 ============

    @Test
    void testListHistory() {
        ZMistSecretHistory h = new ZMistSecretHistory();
        h.setId(1L);
        h.setSecretKey("db");
        secretHistory.onReturn("selectList", Collections.singletonList(h));
        List<ZMistSecretHistory> result = service.listHistory("db", "DEFAULT_GROUP", "");
        assertEquals(1, result.size());
        assertEquals("db", result.get(0).getSecretKey());
    }

    @Test
    void testRollbackToHistorySuccess() {
        ZMistSecretHistory history = new ZMistSecretHistory();
        history.setId(10L);
        history.setSecretKey("rollback_key");
        history.setGroup("DEFAULT_GROUP");
        history.setNamespace("");
        history.setEncryptedValue("old-cipher");
        history.setValueMd5("oldmd5");
        history.setKeyVersion("v1");
        secretHistory.on("selectById", args -> args[0].equals(10L) ? history : null);

        ZMistSecretInfo current = new ZMistSecretInfo();
        current.setId(1L);
        current.setSecretKey("rollback_key");
        current.setGroup("DEFAULT_GROUP");
        current.setNamespace("");
        current.setKeyVersion("v5");
        current.setEncryptedValue("new-cipher");
        current.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        secretInfo.onReturn("selectOne", current);

        ZMistSecretInfo result = service.rollbackToHistory(10L);
        assertNotNull(result);
        // history 的版本 v1 + 自增 → v2
        assertEquals("v2", result.getKeyVersion());
        assertEquals("old-cipher", result.getEncryptedValue());
        assertEquals(1, secretInfo.times("updateById"));
        assertEquals(1, secretHistory.times("insert"), "仅 ROLLBACK 一次");
    }

    @Test
    void testRollbackToHistoryNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.rollbackToHistory(99L));
    }

    // ============ 轮换 ============

    @Test
    void testRotateNowSuccess() {
        ZMistSecretInfo current = new ZMistSecretInfo();
        current.setId(1L);
        current.setSecretKey("rotate_key");
        current.setGroup("DEFAULT_GROUP");
        current.setNamespace("");
        current.setKeyVersion("v1");
        current.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        current.setEncryptedValue("encrypted-old");
        secretInfo.onReturn("selectOne", current);

        ZMistSecretInfo rotated = service.rotateNow("rotate_key", "DEFAULT_GROUP", "", 32);
        assertNotNull(rotated);
        assertEquals("v2", rotated.getKeyVersion());
        assertTrue(secretHistory.times("insert") >= 1);
        assertEquals(1, rotationHistory.times("insert"));
        ZMistRotationHistory rh = (ZMistRotationHistory) rotationHistory.calls("insert").get(0)[0];
        assertEquals("v1", rh.getOldVersion());
        assertEquals("v2", rh.getNewVersion());
        assertEquals("api", rh.getTriggerType());
    }

    @Test
    void testRotateNowSecretNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> service.rotateNow("nonexistent", "DEFAULT_GROUP", "", 32));
    }

    // ============ 动态密钥 ============

    @Test
    void testGenerateDynamicFromExisting() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setId(1L);
        secret.setSecretKey("source_key");
        secret.setGroup("DEFAULT_GROUP");
        secret.setNamespace("");
        secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        secret.setEncryptedValue(service.encryptValue("source-plain", Constance.EncryptAlgorithm.AES));
        secretInfo.onReturn("selectOne", secret);

        String dynKey = service.generateDynamic("source_key", "DEFAULT_GROUP", "", 60, "tester");
        assertNotNull(dynKey);
        assertTrue(dynKey.length() >= 32);
        assertEquals(1, secretDynamic.times("insert"));
        ZMistSecretDynamic saved = (ZMistSecretDynamic) secretDynamic.calls("insert").get(0)[0];
        assertEquals("source_key", saved.getSecretKey());
        assertEquals(60, saved.getTtlSeconds());
        assertEquals(Integer.valueOf(0), saved.getRevoked());
        assertNotNull(saved.getExpireTime());
    }

    @Test
    void testGenerateDynamicWithoutSource() {
        String dynKey = service.generateDynamic(null, null, "", 120, "tester");
        assertNotNull(dynKey);
        assertEquals(0, secretInfo.times("selectOne"), "无源密钥不查库");
        assertEquals(1, secretDynamic.times("insert"));
    }

    @Test
    void testReadDynamicSuccess() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setId(1L);
        secret.setSecretKey("k");
        secret.setGroup("DEFAULT_GROUP");
        secret.setNamespace("");
        secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        secret.setEncryptedValue(service.encryptValue("dyn-plain", Constance.EncryptAlgorithm.AES));
        secretInfo.onReturn("selectOne", secret);

        String dynKey = service.generateDynamic("k", "DEFAULT_GROUP", "", 3600, "u");
        ZMistSecretDynamic stored = new ZMistSecretDynamic();
        stored.setDynKey(dynKey);
        stored.setEncryptedValue(service.eaaSEncrypt("dyn-plain", Constance.EncryptAlgorithm.AES));
        stored.setAlgorithm(Constance.EncryptAlgorithm.AES);
        stored.setRevoked(0);
        stored.setExpireTime(LocalDateTime.now().plusHours(1));
        secretDynamic.onReturn("selectOne", stored);

        String plain = service.readDynamic(dynKey);
        assertEquals("dyn-plain", plain);
    }

    @Test
    void testReadDynamicExpired() {
        ZMistSecretDynamic dyn = new ZMistSecretDynamic();
        dyn.setDynKey("expired-key");
        dyn.setEncryptedValue("x");
        dyn.setAlgorithm(Constance.EncryptAlgorithm.AES);
        dyn.setRevoked(0);
        dyn.setExpireTime(LocalDateTime.now().minusHours(1));
        secretDynamic.onReturn("selectOne", dyn);

        assertThrows(IllegalStateException.class, () -> service.readDynamic("expired-key"));
    }

    @Test
    void testReadDynamicRevoked() {
        ZMistSecretDynamic dyn = new ZMistSecretDynamic();
        dyn.setDynKey("revoked-key");
        dyn.setEncryptedValue("x");
        dyn.setAlgorithm(Constance.EncryptAlgorithm.AES);
        dyn.setRevoked(1);
        dyn.setExpireTime(LocalDateTime.now().plusHours(1));
        secretDynamic.onReturn("selectOne", dyn);

        assertThrows(IllegalStateException.class, () -> service.readDynamic("revoked-key"));
    }

    @Test
    void testReadDynamicNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.readDynamic("nonexistent"));
    }

    @Test
    void testRevokeDynamicSuccess() {
        ZMistSecretDynamic dyn = new ZMistSecretDynamic();
        dyn.setId(1L);
        dyn.setDynKey("revoke-me");
        dyn.setRevoked(0);
        secretDynamic.onReturn("selectOne", dyn);
        secretDynamic.onReturn("updateById", 1);

        assertTrue(service.revokeDynamic("revoke-me"));
    }

    @Test
    void testRevokeDynamicNotFound() {
        assertFalse(service.revokeDynamic("nonexistent"));
    }

    @Test
    void testRevokeDynamicEmpty() {
        assertFalse(service.revokeDynamic(""));
        assertFalse(service.revokeDynamic(null));
    }

    @Test
    void testRecordAccessDoesNotThrow() {
        secretAccessLog.on("insert", args -> {
            throw new RuntimeException("db down");
        });
        assertDoesNotThrow(() -> service.recordAccess("k", "g", "n", "GET",
                "user", "127.0.0.1", true, null));
    }

    @Test
    void testSaveSecretSetsDefaults() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setSecretKey("new-key");
        secret.setSecretName("New");
        secret.setEncryptedValue("plain-value");
        secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);

        ZMistSecretInfo saved = service.saveSecret(secret);
        assertNotNull(saved);
        assertNotNull(saved.getGmtCreate());
        assertNotNull(saved.getGmtModified());
        assertEquals("v1", saved.getKeyVersion());
        assertNotNull(saved.getValueMd5());
        assertNotEquals("plain-value", saved.getEncryptedValue());
        assertEquals(1, secretInfo.times("insert"));
        assertEquals(1, secretHistory.times("insert"));
    }

    @Test
    void testUpdateSecretIncrementsVersion() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setId(1L);
        secret.setSecretKey("k");
        secret.setEncryptedValue("plain");
        secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        secret.setKeyVersion("v3");

        ZMistSecretInfo updated = service.updateSecret(secret);
        assertEquals("v4", updated.getKeyVersion());
        assertEquals(1, secretInfo.times("updateById"));
        assertEquals(1, secretHistory.times("insert"));
    }

    @Test
    void testDeleteSecretSuccess() {
        ZMistSecretInfo secret = new ZMistSecretInfo();
        secret.setId(1L);
        secret.setSecretKey("k");
        secret.setGroup("g");
        secret.setNamespace("n");
        secret.setEncryptedValue("c");
        secret.setValueMd5("m");
        secret.setKeyVersion("v1");
        secretInfo.onReturn("selectOne", secret);
        secretInfo.onReturn("delete", 1);

        assertTrue(service.deleteSecret("k", "g", "n"));
        assertEquals(1, secretHistory.times("insert"));
    }

    @Test
    void testDeleteSecretNotFound() {
        assertFalse(service.deleteSecret("nonexistent", "g", "n"));
    }

    @Test
    void testListSecrets() {
        ZMistSecretInfo s1 = new ZMistSecretInfo();
        secretInfo.onReturn("selectList", Collections.singletonList(s1));
        List<ZMistSecretInfo> result = service.listSecrets("g", "app", "n");
        assertEquals(1, result.size());
    }

    // ================================================================
    // FEATURE027: 密文格式 / 存量兼容 / RSA 落库闸门 / 主密钥轮换不改全局属性
    // 全部为实测坐实的缺陷的回归闸, 每一道都能在旧实现上跑红。
    // ================================================================

    private static final String SYS_PROP = "z-mist.master-key";

    /** 还原全局主密钥系统属性, 避免污染同 JVM 内其它测试。 */
    @AfterEach
    void clearMasterKeySysProp() {
        System.clearProperty(SYS_PROP);
    }

    /**
     * 旧实现用的是 {@code Cipher.getInstance("AES")}，即 AES/ECB/PKCS5Padding。
     * 这里逐字节复刻它，用来证明<b>存量密文不需要迁移</b>。
     */
    private static String legacyEcbEncrypt(String plain, String masterKey) throws Exception {
        byte[] key = java.security.MessageDigest.getInstance("MD5")
                .digest(masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"));
        return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(
                plain.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void testLegacyEcbCiphertextStillDecrypts() throws Exception {
        // 迁移安全闸: 本条在"新实现只支持 GCM"时必须红
        String masterKey = "legacy-master-key-0001";
        String legacyCipher = legacyEcbEncrypt("存量密文必须还能读出来", masterKey);
        assertTrue(MistCrypto.isLegacy(legacyCipher), "存量 ECB 密文应被识别为 legacy");

        System.setProperty(SYS_PROP, masterKey);
        assertEquals("存量密文必须还能读出来",
                service.decryptValue(legacyCipher, Constance.EncryptAlgorithm.AES));
    }

    @Test
    void testLegacyCiphertextCanNeverCollideWithNewFormat() throws Exception {
        // Base64 字母表不含 ':', 所以 'zm1:' 前缀与存量密文永不含糊 —— 这是分流无歧义的前提
        for (int i = 0; i < 200; i++) {
            String legacy = legacyEcbEncrypt("v" + i, "k");
            assertTrue(MistCrypto.isLegacy(legacy));
            assertFalse(legacy.startsWith("zm1:"));
        }
    }

    @Test
    void testAesCiphertextIsNotDeterministic() {
        // ECB 闸: 旧实现同一明文两次密文完全相同
        String plain = "same-secret-value";
        String c1 = service.encryptValue(plain, Constance.EncryptAlgorithm.AES);
        String c2 = service.encryptValue(plain, Constance.EncryptAlgorithm.AES);
        assertNotEquals(c1, c2, "同一明文两次加密必须得到不同密文（随机 nonce）");
        assertEquals(plain, service.decryptValue(c1, Constance.EncryptAlgorithm.AES));
        assertEquals(plain, service.decryptValue(c2, Constance.EncryptAlgorithm.AES));
    }

    @Test
    void testAesCiphertextDoesNotLeakEqualBlocks() {
        // 结构泄露闸: 两条明文首 16 字节(AES 一个块)完全相同, 密文首块必须不同
        String p1 = "0000000000000001:pw-alpha";
        String p2 = "0000000000000001:pw-bravo";
        assertEquals(p1.substring(0, 16), p2.substring(0, 16));

        byte[] c1 = gcmBody(service.encryptValue(p1, Constance.EncryptAlgorithm.AES));
        byte[] c2 = gcmBody(service.encryptValue(p2, Constance.EncryptAlgorithm.AES));
        // GCM: [nonce 12][ciphertext][tag 16]; 比较密文首块(跳过 nonce)
        assertFalse(Arrays.equals(Arrays.copyOfRange(c1, 12, 28), Arrays.copyOfRange(c2, 12, 28)),
                "首块相同的明文不应产出相同的密文首块");
    }

    /** 剥掉 "zm1:" 前缀解 base64, 得到 GCM 的 [nonce|ct|tag] 原始字节。 */
    private static byte[] gcmBody(String stored) {
        assertTrue(stored.startsWith("zm1:"), "本测试只处理新格式, 实际: " + stored);
        return java.util.Base64.getDecoder().decode(stored.substring("zm1:".length()));
    }

    @Test
    void testNewCiphertextCarriesFormatPrefix() {
        String cipher = service.encryptValue("x", Constance.EncryptAlgorithm.AES);
        assertTrue(cipher.startsWith("zm1:"), "新密文应带版本前缀");
        assertFalse(MistCrypto.isLegacy(cipher));
    }

    @Test
    void testSaveSecretRejectsRsa() {
        // RSA 落库闸: RSA KeyPair 只存在于进程内, 落库的密文重启后永久解不开
        ZMistSecretInfo s = new ZMistSecretInfo();
        s.setSecretKey("k");
        s.setGroup("g");
        s.setNamespace("n");
        s.setEncryptedValue("plain");
        s.setEncryptAlgorithm(Constance.EncryptAlgorithm.RSA);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.saveSecret(s));
        assertTrue(ex.getMessage().contains("RSA"), "报错要说清是 RSA 的问题");
        assertEquals(0, secretInfo.times("insert"), "被拒的请求不许落库");
    }

    @Test
    void testUpdateSecretRejectsRsa() {
        ZMistSecretInfo s = new ZMistSecretInfo();
        s.setId(1L);
        s.setSecretKey("k");
        s.setGroup("g");
        s.setNamespace("n");
        s.setEncryptedValue("plain");
        s.setEncryptAlgorithm(Constance.EncryptAlgorithm.RSA);

        assertThrows(IllegalArgumentException.class, () -> service.updateSecret(s));
        assertEquals(0, secretInfo.times("updateById"), "被拒的请求不许落库");
    }

    @Test
    void testEaaSStillAllowsRsa() {
        // 闸门的边界: 不落库的 EaaS 加解密仍应放行 RSA
        String cipher = service.eaaSEncrypt("eaas-rsa", Constance.EncryptAlgorithm.RSA);
        assertEquals("eaas-rsa", service.eaaSDecrypt(cipher, Constance.EncryptAlgorithm.RSA));
    }

    @Test
    void testExplicitKeyMethodsDoNotMutateGlobalMasterKey() throws Exception {
        // 轮换回归闸: 早前是每迁一行就 System.setProperty 在 oldKey/newKey 之间来回切,
        // 并发进来的普通读写会读到改到一半的值。这里断言整个过程全局属性一动不动。
        String oldKey = "the-live-master-key";
        String newKey = "the-brand-new-master-key";
        System.setProperty(SYS_PROP, oldKey);

        String oldCipher = service.encryptValue("payload", Constance.EncryptAlgorithm.AES);
        assertEquals(oldKey, System.getProperty(SYS_PROP), "前置: 全局属性是旧主密钥");

        String plain = service.decryptValueWithKey(oldCipher, Constance.EncryptAlgorithm.AES, oldKey);
        String newCipher = service.encryptValueWithKey(plain, Constance.EncryptAlgorithm.AES, newKey);

        assertEquals(oldKey, System.getProperty(SYS_PROP),
                "轮换过程中的 decryptValueWithKey/encryptValueWithKey 不许改全局主密钥");
    }

    @Test
    void testExplicitKeyRoundTripIsKeyScoped() throws Exception {
        String keyA = "master-key-aaaa";
        String keyB = "master-key-bbbb";
        String cipher = service.encryptValueWithKey("secret", Constance.EncryptAlgorithm.AES, keyA);

        assertEquals("secret", service.decryptValueWithKey(cipher, Constance.EncryptAlgorithm.AES, keyA));
        assertThrows(RuntimeException.class,
                () -> service.decryptValueWithKey(cipher, Constance.EncryptAlgorithm.AES, keyB),
                "换一把主密钥必须解不开");
    }

    @Test
    void testRotatedCiphertextIsNotReadableByOldLiveMasterKey() throws Exception {
        // 轮换语义闸: 用新主密钥重加密的密文, 进程内仍在用的旧主密钥必须读不出来
        // —— 否则说明"迁移"其实没换密钥, 看着成功实则没轮换。
        String oldKey = "live-old-key";
        String newKey = "rotated-new-key";
        System.setProperty(SYS_PROP, oldKey);

        String oldCipher = service.encryptValue("secret", Constance.EncryptAlgorithm.AES);
        String plain = service.decryptValueWithKey(oldCipher, Constance.EncryptAlgorithm.AES, oldKey);
        String rotated = service.encryptValueWithKey(plain, Constance.EncryptAlgorithm.AES, newKey);

        assertThrows(RuntimeException.class,
                () -> service.decryptValue(rotated, Constance.EncryptAlgorithm.AES),
                "已轮换的密文不该再被旧主密钥解出来");
        // 换到新主密钥后即可读出
        System.setProperty(SYS_PROP, newKey);
        assertEquals("secret", service.decryptValue(rotated, Constance.EncryptAlgorithm.AES));
    }

    @Test
    void testLegacyCiphertextSurvivesMasterKeyRotation() throws Exception {
        // 轮换 + 存量格式的组合: 存量 ECB 密文迁到新主密钥后仍应能读
        String oldKey = "rot-old-key";
        String newKey = "rot-new-key";
        String legacy = legacyEcbEncrypt("old-format-value", oldKey);

        String plain = service.decryptValueWithKey(legacy, Constance.EncryptAlgorithm.AES, oldKey);
        String migrated = service.encryptValueWithKey(plain, Constance.EncryptAlgorithm.AES, newKey);

        System.setProperty(SYS_PROP, newKey);
        assertEquals("old-format-value", service.decryptValue(migrated, Constance.EncryptAlgorithm.AES));
    }
}
