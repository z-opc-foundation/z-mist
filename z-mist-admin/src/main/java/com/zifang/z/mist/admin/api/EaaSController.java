package com.zifang.z.mist.admin.api;

import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 加密即服务 EaaS 端点(FEATURE026 P0 + FEATURE065 审计增强).
 * <p>
 * 对应 Vault Transit Engine: 提供加解密能力,不存储数据。
 * 业务方可以把任意明文加密后存到 MySQL/Redis,后续通过 decrypt 反查。
 * <p>
 * FEATURE065: 所有 EaaS 操作记录到 access_log(op_type=EaaS_ENCRYPT/EaaS_DECRYPT)
 * 便于审计追踪，即使不涉及 secret_key。
 *
 * <ul>
 *   <li>POST /api/eaas/encrypt — 加密</li>
 *   <li>POST /api/eaas/decrypt — 解密</li>
 * </ul>
 */
@Tag(name = "加密即服务 EaaS(FEATURE026)")
@RestController
@RequestMapping("/api/eaas")
public class EaaSController {

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "加密(不落库)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/encrypt")
    public Map<String, Object> encrypt(@RequestBody EaaSRequest req, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            if (req == null || req.plainText == null) {
                result.put("success", false);
                result.put("message", "plainText must not be null");
                return result;
            }
            String algorithm = req.algorithm == null ? "AES" : req.algorithm;
            String cipher = secretService.eaaSEncrypt(req.plainText, algorithm);
            result.put("success", true);
            result.put("data", cipher);
            result.put("algorithm", algorithm);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            // FEATURE065: EaaS 操作审计
            secretService.recordAccess("EaaS", null, null, "EaaS_ENCRYPT",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "解密(不落库)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @PostMapping("/decrypt")
    public Map<String, Object> decrypt(@RequestBody EaaSRequest req, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            if (req == null || req.cipherText == null) {
                result.put("success", false);
                result.put("message", "cipherText must not be null");
                return result;
            }
            String algorithm = req.algorithm == null ? "AES" : req.algorithm;
            String plain = secretService.eaaSDecrypt(req.cipherText, algorithm);
            result.put("success", true);
            result.put("data", plain);
            result.put("algorithm", algorithm);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            // FEATURE065: EaaS 操作审计
            secretService.recordAccess("EaaS", null, null, "EaaS_DECRYPT",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    private String currentOperator(HttpServletRequest request) {
        String xff = request.getHeader("X-Staff-No");
        if (xff != null && !xff.isEmpty()) {
            return xff;
        }
        return "anonymous";
    }

    private String currentIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * EaaS 请求体: 加密时 plainText 必填, 解密时 cipherText 必填.
     */
    public static class EaaSRequest {
        public String plainText;
        public String cipherText;
        public String algorithm;
    }
}
