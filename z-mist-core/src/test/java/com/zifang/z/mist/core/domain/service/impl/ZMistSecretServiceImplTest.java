package com.zifang.z.mist.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.core.domain.entity.*;
import com.zifang.z.mist.core.domain.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ZMistSecretServiceImpl 单元测试(FEATURE026).
 * <p>
 * 覆盖: AES / RSA 加密解密、EaaS、搜索、历史、回滚、轮换、动态密钥。
 * 用反射注入私有 mapper 字段,纯 mock,不依赖 Spring/数据库。
 */
public class ZMistSecretServiceImplTest {

    private ZMistSecretServiceImpl service;
    private ZMistSecretInfoMapper secretInfoMapper;
    private ZMistSecretHistoryMapper secretHistoryMapper;
    private ZMistSecretAccessLogMapper secretAccessLogMapper;
    private ZMistSecretDynamicMapper secretDynamicMapper;
    private ZMistRotationHistoryMapper rotationHistoryMapper;

    @BeforeEach
    void setUp() throws Exception {
        service = new ZMistSecretServiceImpl();
        secretInfoMapper = mock(ZMistSecretInfoMapper.class);
        secretHistoryMapper = mock(ZMistSecretHistoryMapper.class);
        secretAccessLogMapper = mock(ZMistSecretAccessLogMapper.class);
        secretDynamicMapper = mock(ZMistSecretDynamicMapper.class);
        rotationHistoryMapper = mock(ZMistRotationHistoryMapper.class);

        setField("secretInfoMapper", secretInfoMapper);
        setField("secretHistoryMapper", secretHistoryMapper);
        setField("secretAccessLogMapper", secretAccessLogMapper);
        setField("secretDynamicMapper", secretDynamicMapper);
        setField("rotationHistoryMapper", rotationHistoryMapper);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = ZMistSecretServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
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
        verify(secretInfoMapper, never()).insert(any(ZMistSecretInfo.class));
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
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(secret);

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
        when(secretInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(s1, s2));

        List<ZMistSecretInfo> result = service.searchSecrets("db", null, null);
        assertEquals(2, result.size());
        verify(secretInfoMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void testSearchSecretsEmptyKeyword() {
        when(secretInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        List<ZMistSecretInfo> result = service.searchSecrets("", null, null);
        assertEquals(0, result.size());
    }

    // ============ 历史 & 回滚 ============

    @Test
    void testListHistory() {
        ZMistSecretHistory h = new ZMistSecretHistory();
        h.setId(1L);
        h.setSecretKey("db");
        when(secretHistoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(h));
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
        when(secretHistoryMapper.selectById(10L)).thenReturn(history);

        ZMistSecretInfo current = new ZMistSecretInfo();
        current.setId(1L);
        current.setSecretKey("rollback_key");
        current.setGroup("DEFAULT_GROUP");
        current.setNamespace("");
        current.setKeyVersion("v5");
        current.setEncryptedValue("new-cipher");
        current.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current);

        ZMistSecretInfo result = service.rollbackToHistory(10L);
        assertNotNull(result);
        // history 的版本 v1 + 自增 → v2
        assertEquals("v2", result.getKeyVersion());
        assertEquals("old-cipher", result.getEncryptedValue());
        verify(secretInfoMapper).updateById(any(ZMistSecretInfo.class));
        verify(secretHistoryMapper, times(1)).insert(any(ZMistSecretHistory.class)); // 仅 ROLLBACK 一次
    }

    @Test
    void testRollbackToHistoryNotFound() {
        when(secretHistoryMapper.selectById(99L)).thenReturn(null);
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
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current);

        ZMistSecretInfo rotated = service.rotateNow("rotate_key", "DEFAULT_GROUP", "", 32);
        assertNotNull(rotated);
        assertEquals("v2", rotated.getKeyVersion());
        verify(secretHistoryMapper, atLeastOnce()).insert(any(ZMistSecretHistory.class));
        ArgumentCaptor<ZMistRotationHistory> captor = ArgumentCaptor.forClass(ZMistRotationHistory.class);
        verify(rotationHistoryMapper).insert(captor.capture());
        assertEquals("v1", captor.getValue().getOldVersion());
        assertEquals("v2", captor.getValue().getNewVersion());
        assertEquals("api", captor.getValue().getTriggerType());
    }

    @Test
    void testRotateNowSecretNotFound() {
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
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
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(secret);

        String dynKey = service.generateDynamic("source_key", "DEFAULT_GROUP", "", 60, "tester");
        assertNotNull(dynKey);
        assertTrue(dynKey.length() >= 32);
        ArgumentCaptor<ZMistSecretDynamic> captor = ArgumentCaptor.forClass(ZMistSecretDynamic.class);
        verify(secretDynamicMapper).insert(captor.capture());
        assertEquals("source_key", captor.getValue().getSecretKey());
        assertEquals(60, captor.getValue().getTtlSeconds());
        assertEquals(Integer.valueOf(0), captor.getValue().getRevoked());
        assertNotNull(captor.getValue().getExpireTime());
    }

    @Test
    void testGenerateDynamicWithoutSource() {
        String dynKey = service.generateDynamic(null, null, "", 120, "tester");
        assertNotNull(dynKey);
        verify(secretInfoMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(secretDynamicMapper).insert(any(ZMistSecretDynamic.class));
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
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(secret);

        String dynKey = service.generateDynamic("k", "DEFAULT_GROUP", "", 3600, "u");
        ZMistSecretDynamic stored = new ZMistSecretDynamic();
        stored.setDynKey(dynKey);
        stored.setEncryptedValue(service.eaaSEncrypt("dyn-plain", Constance.EncryptAlgorithm.AES));
        stored.setAlgorithm(Constance.EncryptAlgorithm.AES);
        stored.setRevoked(0);
        stored.setExpireTime(LocalDateTime.now().plusHours(1));
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

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
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dyn);

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
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dyn);

        assertThrows(IllegalStateException.class, () -> service.readDynamic("revoked-key"));
    }

    @Test
    void testReadDynamicNotFound() {
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.readDynamic("nonexistent"));
    }

    @Test
    void testRevokeDynamicSuccess() {
        ZMistSecretDynamic dyn = new ZMistSecretDynamic();
        dyn.setId(1L);
        dyn.setDynKey("revoke-me");
        dyn.setRevoked(0);
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dyn);
        when(secretDynamicMapper.updateById(any(ZMistSecretDynamic.class))).thenReturn(1);

        assertTrue(service.revokeDynamic("revoke-me"));
    }

    @Test
    void testRevokeDynamicNotFound() {
        when(secretDynamicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.revokeDynamic("nonexistent"));
    }

    @Test
    void testRevokeDynamicEmpty() {
        assertFalse(service.revokeDynamic(""));
        assertFalse(service.revokeDynamic(null));
    }

    @Test
    void testRecordAccessDoesNotThrow() {
        doThrow(new RuntimeException("db down")).when(secretAccessLogMapper)
                .insert(any(ZMistSecretAccessLog.class));
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
        verify(secretInfoMapper).insert(any(ZMistSecretInfo.class));
        verify(secretHistoryMapper).insert(any(ZMistSecretHistory.class));
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
        verify(secretInfoMapper).updateById(any(ZMistSecretInfo.class));
        verify(secretHistoryMapper).insert(any(ZMistSecretHistory.class));
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
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(secret);
        when(secretInfoMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        assertTrue(service.deleteSecret("k", "g", "n"));
        verify(secretHistoryMapper).insert(any(ZMistSecretHistory.class));
    }

    @Test
    void testDeleteSecretNotFound() {
        when(secretInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.deleteSecret("nonexistent", "g", "n"));
    }

    @Test
    void testListSecrets() {
        ZMistSecretInfo s1 = new ZMistSecretInfo();
        when(secretInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(s1));
        List<ZMistSecretInfo> result = service.listSecrets("g", "app", "n");
        assertEquals(1, result.size());
    }
}
