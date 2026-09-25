package com.zifang.z.mist.core.domain.service.impl;

import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.core.domain.entity.*;
import com.zifang.z.mist.core.domain.mapper.*;
import com.zifang.z.mist.core.support.Proxies;
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
}
