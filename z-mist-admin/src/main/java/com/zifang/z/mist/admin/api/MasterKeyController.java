package com.zifang.z.mist.admin.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.core.domain.entity.ZMistMasterKeyHistory;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.mapper.ZMistMasterKeyHistoryMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretInfoMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import com.zifang.z.mist.core.domain.service.impl.ZMistSecretServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主密钥管理 + 信封加密 + 批量导入导出(FEATURE026 P2).
 * <p>
 * 三个能力的端点合集,集中在同一 Controller 方便运维操作:
 * <ul>
 *   <li>POST /api/master-key/rotate      — 滚动主密钥(re-encrypt 所有密钥值)</li>
 *   <li>GET  /api/master-key/list        — 查看主密钥历史</li>
 *   <li>POST /api/master-key/envelope    — 信封加密: 生成数据密钥 + 用主密钥加密数据密钥</li>
 *   <li>POST /api/master-key/unenvelope  — 解封: 用主密钥解出数据密钥再解密业务数据</li>
 *   <li>POST /api/bulk/import            — 批量导入(JSON 数组)</li>
 *   <li>GET  /api/bulk/export            — 批量导出</li>
 * </ul>
 */
@Tag(name = "主密钥+批量(FEATURE026)")
@RestController
@RequestMapping("/api")
public class MasterKeyController {

    private static final Logger log = LogManager.getLogger(MasterKeyController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private ZMistMasterKeyHistoryMapper masterKeyMapper;

    @Autowired
    private ZMistSecretInfoMapper secretInfoMapper;

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "查看主密钥历史")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/master-key/list")
    public Map<String, Object> listMasterKeys() {
        Map<String, Object> result = new HashMap<>();
        List<ZMistMasterKeyHistory> list = masterKeyMapper.selectList(
                new LambdaQueryWrapper<ZMistMasterKeyHistory>().orderByDesc(ZMistMasterKeyHistory::getId));
        result.put("success", true);
        result.put("data", list);
        return result;
    }

    @Operation(summary = "主密钥轮换(re-encrypt 所有密钥值)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/master-key/rotate")
    public Map<String, Object> rotate(@RequestParam(required = false) String newMasterKey) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1) 生成新主密钥 (32 字节十六进制, 64 字符)
            String newKey;
            if (newMasterKey == null || newMasterKey.isEmpty()) {
                newKey = generateRandomHex(64);
            } else {
                newKey = newMasterKey;
            }
            String newMd5 = DigestUtils.md5DigestAsHex(newKey.getBytes(StandardCharsets.UTF_8));

            // 2) 写入新主密钥历史(enabled=0,等所有 re-encrypt 完成再 enable)
            ZMistMasterKeyHistory newRecord = new ZMistMasterKeyHistory();
            newRecord.setKeyAlias("mist-default");
            newRecord.setKeyVersion("v" + System.currentTimeMillis());
            newRecord.setKeyMd5(newMd5);
            newRecord.setAlgorithm("AES");
            newRecord.setEnabled(0);
            newRecord.setCreator("api");
            newRecord.setGmtCreate(LocalDateTime.now());
            masterKeyMapper.insert(newRecord);

            // 3) 用旧主密钥解密 + 新主密钥加密(re-encrypt)
            String oldKey = ZMistSecretServiceImpl.resolveMasterKey();
            // 临时切换主密钥到新值, 调用 encryptValue 会用新密钥
            // 为不污染全局, 这里直接调用 decryptValue(old) + encryptValue(new), 但 encryptValue 用的是全局 master key
            // 所以通过反射注入系统属性 z-mist.master-key, 完成后再恢复
            String originalSysProp = System.getProperty("z-mist.master-key");
            System.setProperty("z-mist.master-key", newKey);
            try {
                List<ZMistSecretInfo> all = secretInfoMapper.selectList(null);
                int migrated = 0;
                for (ZMistSecretInfo secret : all) {
                    // 先用旧密钥解 (这里 encryptValue/decryptValue 都读当前 sys prop, 我们已设为 newKey)
                    // 所以需要还原到 oldKey 解, 然后再设回 newKey 加
                    System.setProperty("z-mist.master-key", oldKey);
                    String plain;
                    try {
                        plain = secretService.decryptValue(secret.getEncryptedValue(), secret.getEncryptAlgorithm());
                    } catch (Exception ex) {
                        log.warn("[z-mist rotate] skip {}: decrypt failed with old key - {}",
                                secret.getSecretKey(), ex.getMessage());
                        continue;
                    }
                    System.setProperty("z-mist.master-key", newKey);
                    String reEncrypted = secretService.encryptValue(plain, secret.getEncryptAlgorithm());
                    secret.setEncryptedValue(reEncrypted);
                    secret.setValueMd5(DigestUtils.md5DigestAsHex(reEncrypted.getBytes(StandardCharsets.UTF_8)));
                    secretInfoMapper.updateById(secret);
                    migrated++;
                }
                // 4) 启用新主密钥
                newRecord.setEnabled(1);
                newRecord.setActivatedTime(LocalDateTime.now());
                masterKeyMapper.updateById(newRecord);
                // 5) 停用老主密钥
                ZMistMasterKeyHistory oldEnabled = masterKeyMapper.selectOne(
                        new LambdaQueryWrapper<ZMistMasterKeyHistory>()
                                .eq(ZMistMasterKeyHistory::getKeyAlias, "mist-default")
                                .eq(ZMistMasterKeyHistory::getEnabled, 1)
                                .ne(ZMistMasterKeyHistory::getId, newRecord.getId()));
                if (oldEnabled != null) {
                    oldEnabled.setEnabled(0);
                    oldEnabled.setDeactivatedTime(LocalDateTime.now());
                    masterKeyMapper.updateById(oldEnabled);
                }
                result.put("success", true);
                result.put("newVersion", newRecord.getKeyVersion());
                result.put("newMd5", newMd5);
                result.put("migratedSecrets", migrated);
                result.put("message", "主密钥轮换完成。请将新主密钥安全保存到外部密钥管理系统, " +
                        "并设置 JVM 系统属性 -Dz-mist.master-key=" + newKey + " 后重启服务");
            } finally {
                // 还原系统属性
                if (originalSysProp != null) {
                    System.setProperty("z-mist.master-key", originalSysProp);
                } else {
                    System.clearProperty("z-mist.master-key");
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "轮换失败: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "信封加密(主密钥+数据密钥)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/master-key/envelope")
    public Map<String, Object> envelope(@RequestParam String plainText) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1) 生成数据密钥(32 字节 Base64)
            byte[] dataKeyBytes = new byte[32];
            RANDOM.nextBytes(dataKeyBytes);
            String dataKey = java.util.Base64.getEncoder().encodeToString(dataKeyBytes);
            // 2) 用主密钥加密数据密钥
            String wrappedKey = secretService.encryptValue(dataKey, "AES");
            // 3) 用数据密钥加密业务数据(AES-256)
            javax.crypto.spec.SecretKeySpec dataKeySpec =
                    new javax.crypto.spec.SecretKeySpec(dataKeyBytes, "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, dataKeySpec);
            String cipherText = java.util.Base64.getEncoder().encodeToString(
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("wrappedKey", wrappedKey);
            data.put("cipherText", cipherText);
            data.put("algorithm", "AES-256");
            result.put("data", data);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "解封(主密钥解数据密钥 + 数据密钥解业务数据)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @PostMapping("/master-key/unenvelope")
    public Map<String, Object> unenvelope(
            @RequestParam String wrappedKey,
            @RequestParam String cipherText) {
        Map<String, Object> result = new HashMap<>();
        try {
            String dataKey = secretService.decryptValue(wrappedKey, "AES");
            byte[] dataKeyBytes = java.util.Base64.getDecoder().decode(dataKey);
            javax.crypto.spec.SecretKeySpec dataKeySpec =
                    new javax.crypto.spec.SecretKeySpec(dataKeyBytes, "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, dataKeySpec);
            String plain = new String(cipher.doFinal(
                    java.util.Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
            result.put("success", true);
            result.put("data", plain);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "批量导入密钥(JSON 数组)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/bulk/import")
    public Map<String, Object> bulkImport(@RequestBody List<ZMistSecretInfo> secrets) {
        Map<String, Object> result = new HashMap<>();
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (ZMistSecretInfo s : secrets) {
            try {
                secretService.saveSecret(s);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(s.getSecretKey() + ": " + e.getMessage());
            }
        }
        result.put("success", failed == 0);
        result.put("total", secrets.size());
        result.put("imported", success);
        result.put("failed", failed);
        if (!errors.isEmpty()) {
            result.put("errors", errors.size() > 10 ? errors.subList(0, 10) : errors);
        }
        return result;
    }

    @Operation(summary = "批量导出密钥")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/bulk/export")
    public Map<String, Object> bulkExport(
            @RequestParam(required = false) String group,
            @RequestParam(required = false, defaultValue = "") String namespace) {
        Map<String, Object> result = new HashMap<>();
        List<ZMistSecretInfo> list = secretService.listSecrets(group, null, namespace);
        result.put("success", true);
        result.put("data", list);
        result.put("total", list.size());
        result.put("exportedAt", LocalDateTime.now());
        result.put("note", "encryptedValue 字段为密文 Base64, 不可直接使用,请通过 /api/secret/plain 取明文");
        return result;
    }

    private String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
