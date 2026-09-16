package io.github.yuku123.z.mist.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.yuku123.z.mist.common.Constance;
import io.github.yuku123.z.mist.core.domain.entity.*;
import io.github.yuku123.z.mist.core.domain.mapper.*;
import io.github.yuku123.z.mist.core.domain.service.IZMistSecretService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥服务实现(FEATURE026:全功能增强).
 * <p>
 * P0: RSA 真实现 / EaaS 加密即服务 / 解密端点 / 历史回滚 / 搜索
 * P1: 自动轮换 / 动态密钥 / 标签 / 过期告警 / 统计
 *
 * @author zifang
 * @see IZMistSecretService
 * @since 1.0.0
 */
@Service
@org.springframework.context.annotation.Primary
public class ZMistSecretServiceImpl extends ServiceImpl<ZMistSecretInfoMapper, ZMistSecretInfo> implements IZMistSecretService {

    private static final String DEFAULT_MASTER_KEY = "z-mist-default-master-key-2024";
    private static final Logger log = LogManager.getLogger(ZMistSecretServiceImpl.class);
    /**
     * FEATURE026 P0: RSA 真实现. 用主密钥派生 RSA KeyPair(同一 master key 同进程内稳定缓存).
     */
    private final ConcurrentHashMap<String, KeyPair> rsaKeyPairCache = new ConcurrentHashMap<>();
    @Autowired
    private ZMistSecretInfoMapper secretInfoMapper;
    @Autowired
    private ZMistSecretHistoryMapper secretHistoryMapper;
    @Autowired
    private ZMistSecretAccessLogMapper secretAccessLogMapper;
    @Autowired
    private ZMistSecretDynamicMapper secretDynamicMapper;
    @Autowired
    private ZMistRotationHistoryMapper rotationHistoryMapper;

    // ============ 基础 CRUD ============

    /**
     * FEATURE022: 拿主密钥.优先级:
     * 1) JVM 系统属性 z-mist.master-key(运维最常用)
     * 2) 环境变量 Z_MIST_MASTER_KEY
     * 3) 编译时常量 DEFAULT_MASTER_KEY(仅 dev 兜底, warning 日志会输出)
     */
    public static String resolveMasterKey() {
        String sysProp = System.getProperty("z-mist.master-key");
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        String env = System.getenv("Z_MIST_MASTER_KEY");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        LogManager.getLogger(ZMistSecretServiceImpl.class)
                .warn("[z-mist] Using DEFAULT_MASTER_KEY. " +
                        "Set system property 'z-mist.master-key' or env 'Z_MIST_MASTER_KEY' for production.");
        return DEFAULT_MASTER_KEY;
    }

    @Override
    public ZMistSecretInfo saveSecret(ZMistSecretInfo secret) {
        LocalDateTime now = LocalDateTime.now();
        secret.setGmtCreate(now);
        secret.setGmtModified(now);
        secret.setKeyVersion("v1");

        String encryptedValue = encryptValue(secret.getEncryptedValue(), secret.getEncryptAlgorithm());
        secret.setEncryptedValue(encryptedValue);
        secret.setValueMd5(calculateMd5(secret.getEncryptedValue()));

        secretInfoMapper.insert(secret);
        saveHistory(secret, "INSERT");
        return secret;
    }

    @Override
    public ZMistSecretInfo updateSecret(ZMistSecretInfo secret) {
        LocalDateTime now = LocalDateTime.now();
        secret.setGmtModified(now);

        String encryptedValue = encryptValue(secret.getEncryptedValue(), secret.getEncryptAlgorithm());
        secret.setEncryptedValue(encryptedValue);
        secret.setValueMd5(calculateMd5(secret.getEncryptedValue()));

        String currentVersion = secret.getKeyVersion();
        if (currentVersion != null && currentVersion.startsWith("v")) {
            try {
                int versionNum = Integer.parseInt(currentVersion.substring(1));
                secret.setKeyVersion("v" + (versionNum + 1));
            } catch (NumberFormatException e) {
                secret.setKeyVersion("v2");
            }
        } else {
            secret.setKeyVersion("v2");
        }

        secretInfoMapper.updateById(secret);
        saveHistory(secret, "UPDATE");
        return secret;
    }

    @Override
    public boolean deleteSecret(String secretKey, String group, String namespace) {
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretInfo::getSecretKey, secretKey)
                .eq(ZMistSecretInfo::getGroup, group)
                .eq(ZMistSecretInfo::getNamespace, namespace);

        ZMistSecretInfo secret = secretInfoMapper.selectOne(wrapper);
        if (secret != null) {
            saveHistory(secret, "DELETE");
            return secretInfoMapper.delete(wrapper) > 0;
        }
        return false;
    }

    @Override
    public ZMistSecretInfo getSecret(String secretKey, String group, String namespace) {
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretInfo::getSecretKey, secretKey)
                .eq(ZMistSecretInfo::getGroup, group)
                .eq(ZMistSecretInfo::getNamespace, namespace);
        return secretInfoMapper.selectOne(wrapper);
    }

    // ============ P0: 加密/解密 ============

    @Override
    public List<ZMistSecretInfo> listSecrets(String group, String appName, String namespace) {
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        if (group != null) {
            wrapper.eq(ZMistSecretInfo::getGroup, group);
        }
        if (appName != null) {
            wrapper.eq(ZMistSecretInfo::getAppName, appName);
        }
        if (namespace != null) {
            wrapper.eq(ZMistSecretInfo::getNamespace, namespace);
        }
        wrapper.orderByDesc(ZMistSecretInfo::getGmtModified);
        return secretInfoMapper.selectList(wrapper);
    }

    @Override
    public String encryptValue(String plainValue, String algorithm) {
        try {
            if (Constance.EncryptAlgorithm.AES.equals(algorithm)) {
                return encryptAES(plainValue);
            } else if (Constance.EncryptAlgorithm.RSA.equals(algorithm)) {
                return encryptRSA(plainValue);
            }
            return encryptAES(plainValue);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String decryptValue(String encryptedValue, String algorithm) {
        try {
            if (Constance.EncryptAlgorithm.AES.equals(algorithm)) {
                return decryptAES(encryptedValue);
            } else if (Constance.EncryptAlgorithm.RSA.equals(algorithm)) {
                return decryptRSA(encryptedValue);
            }
            return decryptAES(encryptedValue);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * P0: AES 加密.
     */
    private String encryptAES(String plainValue) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(getMasterKeyBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * P0: AES 解密.
     */
    private String decryptAES(String encryptedValue) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedValue);
        SecretKeySpec keySpec = new SecretKeySpec(getMasterKeyBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String encryptRSA(String plainValue) throws Exception {
        KeyPair kp = getOrCreateRSAKeyPair();
        PublicKey publicKey = kp.getPublic();
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decryptRSA(String encryptedValue) throws Exception {
        KeyPair kp = getOrCreateRSAKeyPair();
        PrivateKey privateKey = kp.getPrivate();
        byte[] decoded = Base64.getDecoder().decode(encryptedValue);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 主密钥派生 RSA 2048 KeyPair,缓存避免重复生成(生成一次约 100ms).
     */
    private KeyPair getOrCreateRSAKeyPair() throws Exception {
        String mk = resolveMasterKey();
        String cacheKey = "rsa:" + Integer.toHexString(mk.hashCode());
        KeyPair kp = rsaKeyPairCache.get(cacheKey);
        if (kp != null) {
            return kp;
        }
        synchronized (cacheKey.intern()) {
            kp = rsaKeyPairCache.get(cacheKey);
            if (kp != null) {
                return kp;
            }
            // 用主密钥 MD5 作为 RSA 种子,保证可复现
            byte[] seedBytes = MessageDigest.getInstance("MD5").digest(mk.getBytes(StandardCharsets.UTF_8));
            SecureRandom random = new SecureRandom(seedBytes);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, random);
            kp = gen.generateKeyPair();
            rsaKeyPairCache.put(cacheKey, kp);
            log.info("[z-mist] RSA KeyPair generated (2048 bits, alias={})", cacheKey);
            return kp;
        }
    }

    private byte[] getMasterKeyBytes() {
        try {
            String mk = resolveMasterKey();
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(mk.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return DEFAULT_MASTER_KEY.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String calculateMd5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    // ============ P0: EaaS + 解密 + 搜索 + 历史 ============

    @Override
    public String eaaSEncrypt(String plainText, String algorithm) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText must not be null");
        }
        return encryptValue(plainText, algorithm);
    }

    @Override
    public String eaaSDecrypt(String cipherText, String algorithm) {
        if (cipherText == null) {
            throw new IllegalArgumentException("cipherText must not be null");
        }
        return decryptValue(cipherText, algorithm);
    }

    @Override
    public String decryptToPlain(String secretKey, String group, String namespace, String cipherText) {
        String source = cipherText;
        if (source == null || source.isEmpty()) {
            ZMistSecretInfo secret = getSecret(secretKey, group, namespace);
            if (secret == null) {
                throw new IllegalArgumentException("secret not found: " + secretKey);
            }
            source = secret.getEncryptedValue();
            if (source == null) {
                throw new IllegalStateException("encryptedValue is null for " + secretKey);
            }
            return decryptValue(source, secret.getEncryptAlgorithm());
        }
        // 解密外部传入的密文,使用默认 AES
        return decryptValue(source, Constance.EncryptAlgorithm.AES);
    }

    @Override
    public List<ZMistSecretInfo> searchSecrets(String keyword, String group, String namespace) {
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(ZMistSecretInfo::getSecretKey, keyword)
                    .or().like(ZMistSecretInfo::getSecretName, keyword)
                    .or().like(ZMistSecretInfo::getDescription, keyword)
                    .or().like(ZMistSecretInfo::getAppName, keyword));
        }
        if (group != null && !group.isEmpty()) {
            wrapper.eq(ZMistSecretInfo::getGroup, group);
        }
        if (namespace != null && !namespace.isEmpty()) {
            wrapper.eq(ZMistSecretInfo::getNamespace, namespace);
        }
        wrapper.orderByDesc(ZMistSecretInfo::getGmtModified);
        return secretInfoMapper.selectList(wrapper);
    }

    @Override
    public List<ZMistSecretHistory> listHistory(String secretKey, String group, String namespace) {
        LambdaQueryWrapper<ZMistSecretHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretHistory::getSecretKey, secretKey)
                .eq(ZMistSecretHistory::getGroup, group)
                .eq(ZMistSecretHistory::getNamespace, namespace)
                .orderByDesc(ZMistSecretHistory::getGmtCreate);
        return secretHistoryMapper.selectList(wrapper);
    }

    @Override
    public ZMistSecretInfo rollbackToHistory(Long historyId) {
        if (historyId == null) {
            throw new IllegalArgumentException("historyId must not be null");
        }
        ZMistSecretHistory history = secretHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("history not found: " + historyId);
        }
        ZMistSecretInfo current = getSecret(history.getSecretKey(),
                history.getGroup(), history.getNamespace());
        if (current == null) {
            throw new IllegalStateException("current secret not exists: " + history.getSecretKey());
        }
        // 把历史版本的内容写回当前
        current.setEncryptedValue(history.getEncryptedValue());
        current.setValueMd5(history.getValueMd5());
        current.setKeyVersion(history.getKeyVersion());
        LocalDateTime now = LocalDateTime.now();
        current.setGmtModified(now);
        // 版本号自增
        String ver = current.getKeyVersion();
        if (ver != null && ver.startsWith("v")) {
            try {
                current.setKeyVersion("v" + (Integer.parseInt(ver.substring(1)) + 1));
            } catch (NumberFormatException ignore) {
                current.setKeyVersion("v2");
            }
        }
        secretInfoMapper.updateById(current);
        saveHistory(current, "ROLLBACK");
        return current;
    }

    // ============ P1: 自动轮换 ============

    @Override
    public ZMistSecretInfo rotateNow(String secretKey, String group, String namespace, Integer newValueLength) {
        ZMistSecretInfo current = getSecret(secretKey, group, namespace);
        if (current == null) {
            throw new IllegalArgumentException("secret not found: " + secretKey);
        }
        String oldVersion = current.getKeyVersion();
        int len = newValueLength == null ? 32 : newValueLength;
        String newValue = randomAlphanumeric(len);
        current.setEncryptedValue(newValue);
        current.setKeyVersion(oldVersion);
        ZMistSecretInfo updated = updateSecret(current);

        // 写轮换历史
        ZMistRotationHistory rh = new ZMistRotationHistory();
        rh.setSecretKey(secretKey);
        rh.setGroup(group);
        rh.setNamespace(namespace);
        rh.setOldVersion(oldVersion);
        rh.setNewVersion(updated.getKeyVersion());
        rh.setTriggerType("api");
        rh.setSuccess(1);
        rh.setGmtCreate(LocalDateTime.now());
        rotationHistoryMapper.insert(rh);
        return updated;
    }

    // ============ P1: 动态密钥 ============

    @Override
    public String generateDynamic(String secretKey, String group, String namespace, int ttlSeconds, String creator) {
        String plain;
        if (secretKey != null && !secretKey.isEmpty()) {
            ZMistSecretInfo secret = getSecret(secretKey, group, namespace);
            if (secret == null) {
                throw new IllegalArgumentException("source secret not found: " + secretKey);
            }
            plain = decryptValue(secret.getEncryptedValue(), secret.getEncryptAlgorithm());
        } else {
            // 没有源密钥,生成全新随机 32 字节
            plain = randomAlphanumeric(32);
        }
        String encrypted;
        try {
            encrypted = encryptAES(plain);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }

        ZMistSecretDynamic dyn = new ZMistSecretDynamic();
        dyn.setDynKey(UUID.randomUUID().toString());
        dyn.setSecretKey(secretKey);
        dyn.setGroup(group);
        dyn.setNamespace(namespace == null ? "" : namespace);
        dyn.setEncryptedValue(encrypted);
        dyn.setAlgorithm(Constance.EncryptAlgorithm.AES);
        dyn.setLeaseId(UUID.randomUUID().toString());
        dyn.setTtlSeconds(ttlSeconds <= 0 ? 3600 : ttlSeconds);
        dyn.setExpireTime(LocalDateTime.now().plusSeconds(dyn.getTtlSeconds()));
        dyn.setRevoked(0);
        dyn.setCreator(creator == null ? "anonymous" : creator);
        dyn.setGmtCreate(LocalDateTime.now());
        secretDynamicMapper.insert(dyn);
        return dyn.getDynKey();
    }

    @Override
    public String readDynamic(String dynKey) {
        if (dynKey == null || dynKey.isEmpty()) {
            throw new IllegalArgumentException("dynKey must not be empty");
        }
        ZMistSecretDynamic dyn = secretDynamicMapper.selectOne(
                new LambdaQueryWrapper<ZMistSecretDynamic>().eq(ZMistSecretDynamic::getDynKey, dynKey));
        if (dyn == null) {
            throw new IllegalArgumentException("dynamic key not found: " + dynKey);
        }
        if (dyn.getRevoked() != null && dyn.getRevoked() == 1) {
            throw new IllegalStateException("dynamic key revoked: " + dynKey);
        }
        if (dyn.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("dynamic key expired: " + dynKey);
        }
        try {
            return decryptAES(dyn.getEncryptedValue());
        } catch (Exception e) {
            throw new RuntimeException("decrypt dynamic key failed", e);
        }
    }

    @Override
    public boolean revokeDynamic(String dynKey) {
        if (dynKey == null || dynKey.isEmpty()) {
            return false;
        }
        ZMistSecretDynamic dyn = secretDynamicMapper.selectOne(
                new LambdaQueryWrapper<ZMistSecretDynamic>().eq(ZMistSecretDynamic::getDynKey, dynKey));
        if (dyn == null) {
            return false;
        }
        dyn.setRevoked(1);
        dyn.setRevokeTime(LocalDateTime.now());
        return secretDynamicMapper.updateById(dyn) > 0;
    }

    /**
     * 生成指定长度的字母数字随机串.
     */
    private String randomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ============ 工具 ============

    private void saveHistory(ZMistSecretInfo secret, String opType) {
        ZMistSecretHistory history = new ZMistSecretHistory();
        history.setNid(secret.getId());
        history.setSecretKey(secret.getSecretKey());
        history.setGroup(secret.getGroup());
        history.setAppName(secret.getAppName());
        history.setEncryptedValue(secret.getEncryptedValue());
        history.setValueMd5(secret.getValueMd5());
        history.setKeyVersion(secret.getKeyVersion());
        history.setOpType(opType);
        history.setNamespace(secret.getNamespace());
        LocalDateTime now = LocalDateTime.now();
        history.setGmtCreate(now);
        history.setGmtModified(now);
        secretHistoryMapper.insert(history);
    }

    /**
     * FEATURE021: 写访问日志.失败不抛异常 —— 审计失败不能阻塞业务.
     */
    @Override
    public void recordAccess(String secretKey, String group, String namespace,
                             String opType, String operator, String operatorIp,
                             boolean success, String errorMessage) {
        try {
            ZMistSecretAccessLog logRow = new ZMistSecretAccessLog();
            logRow.setSecretKey(secretKey);
            logRow.setGroup(group);
            logRow.setNamespace(namespace);
            logRow.setOpType(opType);
            logRow.setOperator(operator == null ? "anonymous" : operator);
            logRow.setOperatorIp(operatorIp);
            logRow.setSuccess(success);
            logRow.setErrorMessage(errorMessage);
            logRow.setGmtCreate(LocalDateTime.now());
            secretAccessLogMapper.insert(logRow);
        } catch (Exception ex) {
            log.warn("[z-mist] recordAccess failed: secretKey={}, opType={}, err={}",
                    secretKey, opType, ex.getMessage());
        }
    }
}
