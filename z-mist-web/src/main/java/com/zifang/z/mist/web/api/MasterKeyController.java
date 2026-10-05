package com.zifang.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.common.Result;
import com.zifang.z.mist.common.crypto.MistCrypto;
import com.zifang.z.mist.core.domain.entity.ZMistMasterKeyHistory;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.mapper.ZMistMasterKeyHistoryMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretInfoMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import com.zifang.z.mist.core.domain.service.impl.ZMistSecretServiceImpl;
import com.zifang.z.mist.web.api.request.SecretReq;
import com.zifang.z.mist.web.api.response.EnvelopeResp;
import com.zifang.z.mist.web.api.response.MasterKeyHistoryResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeanUtils;
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
@RestController("zMistMasterKeyController")
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
    public Result<List<MasterKeyHistoryResp>> listMasterKeys() {
        Result<List<MasterKeyHistoryResp>> result = new Result<>();
        List<ZMistMasterKeyHistory> list = masterKeyMapper.selectList(
                new LambdaQueryWrapper<ZMistMasterKeyHistory>().orderByDesc(ZMistMasterKeyHistory::getId));
        result.setSuccess(true);
        result.setData(toRespList(list));
        return result;
    }

    @Operation(summary = "主密钥轮换(re-encrypt 所有密钥值)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/master-key/rotate")
    public Map<String, Object> rotate(@RequestParam(required = false) String newMasterKey) {
        // 不规则聚合输出(newVersion/newMd5/migratedSecrets 自定义 key), 保持原状
        Map<String, Object> result = new HashMap<>();
        // 提到 try 外：失败分支也要报出"迁了多少条"，否则运维无从判断要不要重跑
        int migrated = 0;
        int skipped = 0;
        try {
            // 1) 生成新主密钥 (32 字节十六进制, 64 字符)
            String newKey = newMasterKey == null || newMasterKey.isEmpty()
                    ? generateRandomHex(64) : newMasterKey;
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
            //    显式把主密钥传给 service，不动 System property。
            //    早前是"每迁一行就把 z-mist.master-key 在 oldKey/newKey 之间来回 setProperty"，
            //    而 encryptValue/decryptValue 每次都现读这个全局属性 —— 轮换期间并发进来的
            //    普通读写请求会恰好读到被改到一半的值，把和轮换无关的密钥值写成解不开的密文。
            String oldKey = ZMistSecretServiceImpl.resolveMasterKey();
            List<ZMistSecretInfo> all = secretInfoMapper.selectList(null);
            for (ZMistSecretInfo secret : all) {
                String plain;
                try {
                    plain = secretService.decryptValueWithKey(
                            secret.getEncryptedValue(), secret.getEncryptAlgorithm(), oldKey);
                } catch (Exception ex) {
                    // 典型场景：上一次轮换中途失败，这行已经迁到新密钥了 —— 跳过即可，重跑本轮会收敛
                    log.warn("[z-mist rotate] skip {}: decrypt failed with old key - {}",
                            secret.getSecretKey(), ex.getMessage());
                    skipped++;
                    continue;
                }
                String reEncrypted = secretService.encryptValueWithKey(
                        plain, secret.getEncryptAlgorithm(), newKey);
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
            result.put("skippedSecrets", skipped);
            result.put("message", "主密钥轮换完成。请将新主密钥安全保存到外部密钥管理系统, " +
                    "并设置 JVM 系统属性 -Dz-mist.master-key=" + newKey + " 后重启服务");
        } catch (Exception e) {
            result.put("success", false);
            // 中途失败时已迁走的 migrated 行用的是 newKey，而进程仍在用 oldKey ——
            // 不报出这个数，运维会以为"什么都没迁"从而放弃重跑，那 migrated 行就永久读不出来了。
            result.put("migratedSecrets", migrated);
            result.put("skippedSecrets", skipped);
            result.put("message", "轮换失败: " + e.getMessage()
                    + "。已迁移 " + migrated + " 条(这些行现在用的是新主密钥，进程内仍按旧主密钥解密，"
                    + "重跑本接口即可收敛：已迁移的行解密失败会被跳过，其余继续迁)");
        }
        return result;
    }

    @Operation(summary = "信封加密(主密钥+数据密钥)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/master-key/envelope")
    public Result<EnvelopeResp> envelope(@RequestParam String plainText) {
        Result<EnvelopeResp> result = new Result<>();
        try {
            // 1) 生成数据密钥(32 字节 Base64)
            byte[] dataKeyBytes = new byte[32];
            RANDOM.nextBytes(dataKeyBytes);
            String dataKey = java.util.Base64.getEncoder().encodeToString(dataKeyBytes);
            // 2) 用主密钥加密数据密钥
            String wrappedKey = secretService.encryptValue(dataKey, "AES");
            // 3) 用数据密钥加密业务数据(AES-256, GCM + 随机 nonce)
            //    走 MistCrypto：早前是裸 Cipher.getInstance("AES")，即 ECB/PKCS5Padding，
            //    确定性加密、相同明文块产出相同密文块。数据密钥是每条随机生成的，
            //    但同一数据密钥下的重复明文仍会泄露结构，且密文可作等价类判据。
            String cipherText = MistCrypto.encryptWithKey(plainText, dataKeyBytes);
            result.setSuccess(true);
            result.setData(new EnvelopeResp(wrappedKey, cipherText, "AES-256/GCM"));
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    @Operation(summary = "解封(主密钥解数据密钥 + 数据密钥解业务数据)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @PostMapping("/master-key/unenvelope")
    public Result<String> unenvelope(
            @RequestParam String wrappedKey,
            @RequestParam String cipherText) {
        Result<String> result = new Result<>();
        try {
            String dataKey = secretService.decryptValue(wrappedKey, "AES");
            byte[] dataKeyBytes = java.util.Base64.getDecoder().decode(dataKey);
            String plain = MistCrypto.decryptWithKey(cipherText, dataKeyBytes);
            result.setSuccess(true);
            result.setData(plain);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    @Operation(summary = "批量导入密钥(JSON 数组)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/bulk/import")
    public Map<String, Object> bulkImport(@RequestBody List<SecretReq> secrets) {
        // 不规则聚合输出(total/imported/failed/errors 自定义 key), 保持原状
        Map<String, Object> result = new HashMap<>();
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (SecretReq secretReq : secrets) {
            try {
                ZMistSecretInfo entity = new ZMistSecretInfo();
                BeanUtils.copyProperties(secretReq, entity);
                secretService.saveSecret(entity);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(secretReq.getSecretKey() + ": " + e.getMessage());
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
        // 不规则聚合输出(total/exportedAt/note 自定义 key), 保持原状
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

    private List<MasterKeyHistoryResp> toRespList(List<ZMistMasterKeyHistory> list) {
        List<MasterKeyHistoryResp> respList = new ArrayList<>(list.size());
        for (ZMistMasterKeyHistory history : list) {
            MasterKeyHistoryResp resp = new MasterKeyHistoryResp();
            BeanUtils.copyProperties(history, resp);
            respList.add(resp);
        }
        return respList;
    }
}
