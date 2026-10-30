package com.zifang.z.mist.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.common.crypto.MistCrypto;
import com.zifang.z.mist.core.domain.entity.*;
import com.zifang.z.mist.core.domain.mapper.*;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    /** RSA KeyPair 生成的锁。不用 cacheKey.intern()：那会把主密钥塞进 JVM 字符串池且永不回收。 */
    private final Object rsaKeyPairLock = new Object();
    /**
     * 访问审计表缺失时的 ERROR 上报节流窗口(毫秒)。
     * <p>缺表会让每一次密钥访问都写审计失败 ⇒ 不节流则日志被刷爆, 真正的 ERROR 反而被淹掉。
     */
    private static final long AUDIT_TABLE_ERROR_REPORT_INTERVAL_MS = 60_000L;
    /** 上一次"审计表缺失"ERROR 的上报时刻。 */
    private final AtomicLong lastAuditTableErrorReportAt = new AtomicLong(0L);
    /** 节流窗口内被压掉的同类失败次数(下一条 ERROR 一起带出, 不丢信息)。 */
    private final AtomicLong suppressedAuditTableErrors = new AtomicLong(0L);

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

        rejectUnpersistableAlgorithm(secret.getEncryptAlgorithm());
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

        rejectUnpersistableAlgorithm(secret.getEncryptAlgorithm());
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

    /**
     * 拒绝把 RSA 密文落库。
     * <p>
     * RSA KeyPair 是进程级缓存且跨重启不保留（见 {@link #getOrCreateRSAKeyPair}），
     * 用 RSA 加密再写进 {@code z_mist_secret_info} 的值，服务一重启就<b>永久不可解</b>：
     * 重启后拿到的是全新 KeyPair，解密抛 {@code BadPaddingException}，
     * 且本仓不保存历史私钥，没有任何回退路径。
     * <p>
     * 早前这里直接放行，而 {@code encryptAlgorithm} 是 HTTP 请求体里客户端可传的字段
     * （{@code SecretReq.encryptAlgorithm}），等于把"重启即丢数据"的入口开给了调用方。
     * 这里改成写入前 fail-fast：宁可当场报错，也不落一份注定解不开的密文。
     * <p>
     * 确实需要 RSA 的场景走 {@link #eaaSEncrypt}（进程内加解密对，不落库）。
     */
    private void rejectUnpersistableAlgorithm(String algorithm) {
        if (Constance.EncryptAlgorithm.RSA.equals(algorithm)) {
            throw new IllegalArgumentException(
                    "encryptAlgorithm=RSA 不能用于落库：RSA KeyPair 仅存在于当前进程内，重启后无法解密。"
                            + "密钥存储请使用 AES；如确需 RSA，请走 EaaS 加解密接口。");
        }
    }

    /**
     * 密钥列表查询.
     * <p>
     * <b>缺省语义与 {@link #searchSecrets} 对齐</b>: {@code null} 与空串都表示"不过滤"。
     * <p>
     * 早前这里是 {@code if (namespace != null)} —— 空串被当成"精确匹配 namespace=''"，
     * 于是同一个参数名在两个端点有两种口径：
     * {@code /api/secret/list} 不传 namespace ⇒ 只回 namespace 为空串的那几行（实测 total=1），
     * {@code /api/secret/search} 不传 namespace ⇒ 回全量（实测 total=11）。
     * 同一个"缺省"含义在两个接口上给出两份答案，调用方无法判断哪个是全量。
     * <p>
     * 注意：{@link #getSecret} / {@link #deleteSecret} 走的是"按 (secretKey, group, namespace)
     * 精确定位"，那里的无条件 eq 是<b>有意为之</b>，不在本次改动范围内。
     *
     * @param group     分组,缺省/空串 = 不过滤
     * @param appName   应用名,缺省/空串 = 不过滤
     * @param namespace 命名空间,缺省/空串 = 不过滤
     * @return 命中的密钥列表
     */
    @Override
    public List<ZMistSecretInfo> listSecrets(String group, String appName, String namespace) {
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isEmpty()) {
            wrapper.eq(ZMistSecretInfo::getGroup, group);
        }
        if (appName != null && !appName.isEmpty()) {
            wrapper.eq(ZMistSecretInfo::getAppName, appName);
        }
        if (namespace != null && !namespace.isEmpty()) {
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
     * P0: AES 加密（默认取当前主密钥）.
     * <p>
     * 走 {@link MistCrypto}：AES/GCM + 随机 nonce，产出 {@code zm1:} 新格式。
     * 历史实现是 {@code Cipher.getInstance("AES")}（= AES/ECB/PKCS5Padding），确定性加密、
     * 泄露明文块结构，且同一明文两次密文完全相同。
     */
    private String encryptAES(String plainValue) throws Exception {
        return MistCrypto.encrypt(plainValue, resolveMasterKey());
    }

    /**
     * P0: AES 解密（默认取当前主密钥）.
     * <p>
     * 带 {@code zm1:} 前缀走 GCM；不带前缀按存量 AES/ECB/PKCS5Padding 解 —— 存量密文不需要迁移。
     */
    private String decryptAES(String encryptedValue) throws Exception {
        return MistCrypto.decrypt(encryptedValue, resolveMasterKey());
    }

    /**
     * 用<b>显式给定</b>的主密钥解密（主密钥轮换用）.
     * <p>
     * 不碰 {@link #resolveMasterKey()}，因此不需要临时改 JVM 全局系统属性 ——
     * 轮换过程中并发进来的普通读写请求不会读到"改到一半"的主密钥。
     */
    @Override
    public String decryptValueWithKey(String encryptedValue, String algorithm, String masterKey) {
        try {
            if (Constance.EncryptAlgorithm.RSA.equals(algorithm)) {
                return decryptRSA(encryptedValue);
            }
            return MistCrypto.decrypt(encryptedValue, masterKey);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 用<b>显式给定</b>的主密钥加密（主密钥轮换用）。理由同 {@link #decryptValueWithKey}。
     */
    @Override
    public String encryptValueWithKey(String plainValue, String algorithm, String masterKey) {
        try {
            if (Constance.EncryptAlgorithm.RSA.equals(algorithm)) {
                return encryptRSA(plainValue);
            }
            return MistCrypto.encrypt(plainValue, masterKey);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
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
     * 主密钥派生 RSA 2048 KeyPair，进程内缓存（生成一次约 100ms）。
     * <p>
     * <b>注意：这个 KeyPair 是进程级的，跨重启不保留。</b>
     * {@code new SecureRandom(seed)} 在现代 JDK 上<b>不</b>产生确定性序列
     * （实测：同一 seed 连续两次派生，公钥不同），所以本方法做不到"可复现"；
     * 重启后拿到的是全新 KeyPair，重启前落库的 RSA 密文再也解不开
     * （实测 {@code BadPaddingException}），且没有历史私钥可回退。
     * 因此 {@link #saveSecret} / {@link #updateSecret} 已拒绝以 RSA 落库。
     */
    private KeyPair getOrCreateRSAKeyPair() throws Exception {
        String mk = resolveMasterKey();
        // 用完整主密钥做 cacheKey：早前用 Integer.toHexString(mk.hashCode())，
        // 两个不同主密钥一旦 hashCode 碰撞就会共用同一个 KeyPair（拿对方的公钥加密、自己解不开）。
        KeyPair kp = rsaKeyPairCache.get(mk);
        if (kp != null) {
            return kp;
        }
        synchronized (rsaKeyPairLock) {
            kp = rsaKeyPairCache.get(mk);
            if (kp != null) {
                return kp;
            }
            byte[] seedBytes = MessageDigest.getInstance("MD5").digest(mk.getBytes(StandardCharsets.UTF_8));
            SecureRandom random = new SecureRandom(seedBytes);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, random);
            kp = gen.generateKeyPair();
            rsaKeyPairCache.put(mk, kp);
            // 日志里只打 keyId，不打主密钥本身
            log.info("[z-mist] RSA KeyPair generated (2048 bits, keyId={})", keyIdOf(mk));
            return kp;
        }
    }

    /**
     * 主密钥的公开标识：SHA-256 前 4 字节十六进制，仅用于日志/展示，可安全外泄。
     */
    private static String keyIdOf(String masterKey) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
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
     *
     * <p><b>但"不抛异常"不等于"静默"</b>：早前这里是 {@code log.warn(...)}，
     * 缺表（{@code z_mist_secret_access_log} 不存在）时每次读密钥都只留一行 warn，
     * 端点照样 200、页面照样全绿，而"谁在什么时候读了哪个密钥"一条都没落库 ——
     * 静默 catch 把"表根本没建"伪装成"没有数据"。
     * <p>
     * 现在分两种情况：
     * <ul>
     *   <li><b>表不存在/不可用</b> ⇒ 升级为 <b>ERROR</b> 并打上"访问审计未落库"字样，
     *       让运维在告警里能直接看到"合规痕迹正在丢失"；为避免每次请求刷屏，
     *       同一时间窗（60s）内只报一次，其余计数在下一条 ERROR 里带出。</li>
     *   <li><b>其它异常</b> ⇒ 同样升级为 <b>ERROR</b>（审计失败本身就是合规事件，
     *       不该停在 warn 级别被日常日志淹没）。</li>
     * </ul>
     * 另外 {@link #selfCheckAuditTable()} 在启动期主动探一次表，把问题从"运行中静默"
     * 提前到"启动即报"。
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
            if (isMissingTableError(ex)) {
                reportAuditTableMissing(secretKey, opType, ex);
            } else {
                log.error("[z-mist] 访问审计未落库: recordAccess failed: secretKey={}, opType={}, err={}",
                        secretKey, opType, ex.getMessage(), ex);
            }
        }
    }

    /**
     * 启动期自检: 访问审计表到底在不在.
     * <p>
     * 缺表时不能只靠运行期 catch 兜着 —— 那样要等到第一条密钥被读才暴露，
     * 而那之前"审计没在工作"这件事没有任何人知道。这里在 bean 初始化后主动探一次：
     * 表不存在 ⇒ 直接 ERROR，并指明补表脚本位置。
     * <p>
     * <b>不抛异常</b>：审计表缺失不应该让整个服务起不来（读端点仍要可用），
     * 但必须让运维在启动日志里一眼看到。
     */
    @PostConstruct
    public void selfCheckAuditTable() {
        try {
            Long count = secretAccessLogMapper.selectCount(null);
            log.info("[z-mist] 自检通过: 访问审计表可用 (z_mist_secret_access_log, count={})", count);
        } catch (Exception ex) {
            log.error("[z-mist] 自检失败: 访问审计表(z_mist_secret_access_log)不存在或不可用, "
                            + "访问审计将无法落库(密钥被谁读了不会留痕)。"
                            + "请核对并执行 z-mist/_doc/002_deploy/init_missing_tables_oc.sql 补齐缺表。err={}",
                    ex.getMessage(), ex);
        }
    }

    /**
     * 上报"审计表缺失": ERROR 级别 + 节流.
     * <p>
     * 缺表会让<b>每一次</b>密钥访问都失败 ⇒ 不节流的话一次压测就能刷爆日志，
     * 反而把真正的 ERROR 淹掉。所以：首次立即 ERROR；60s 窗口内的同类失败只计数，
     * 下一条 ERROR 把被节流掉的次数一起带出来（不丢信息，也不刷屏）。
     */
    private void reportAuditTableMissing(String secretKey, String opType, Exception ex) {
        long now = System.currentTimeMillis();
        long last = lastAuditTableErrorReportAt.get();
        if (now - last < AUDIT_TABLE_ERROR_REPORT_INTERVAL_MS) {
            suppressedAuditTableErrors.incrementAndGet();
            return;
        }
        lastAuditTableErrorReportAt.set(now);
        long suppressed = suppressedAuditTableErrors.getAndSet(0);
        log.error("[z-mist] 访问审计未落库: 访问日志表(z_mist_secret_access_log)不存在或不可用, "
                        + "密钥访问轨迹正在丢失。secretKey={}, opType={}, err={}, 同类失败被节流={} 次",
                secretKey, opType, ex.getMessage(), suppressed);
    }

    /**
     * 判断异常是不是"表不存在"（缺表 / 库没建 / SQL 语法因缺表而错）.
     * <p>
     * MyBatis 会把底层 SQLException 包成多层异常（MyBatisSystemException →
     * BadSqlGrammarException → SQLException），所以要沿 cause 链一路找。
     */
    private static boolean isMissingTableError(Throwable ex) {
        Throwable t = ex;
        int depth = 0;
        while (t != null && depth++ < 10) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("doesn't exist") || lower.contains("does not exist")
                        || lower.contains("unknown table") || lower.contains("no such table")
                        || lower.contains("table not found") || lower.contains("bad sql grammar")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
