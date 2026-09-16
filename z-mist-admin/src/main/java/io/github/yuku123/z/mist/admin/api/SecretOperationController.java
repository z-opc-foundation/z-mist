package io.github.yuku123.z.mist.admin.api;

import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretHistory;
import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretInfo;
import io.github.yuku123.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 密钥操作扩展端点(FEATURE026 P0).
 * <p>
 * 提供解密端点 / 历史版本 / 回滚 / 搜索 / 轮换 / 动态密钥 等高频操作.
 * 与 SecretController 分开避免单文件过大.
 *
 * <ul>
 *   <li>GET  /api/secret/plain                — 取明文(P0)</li>
 *   <li>GET  /api/secret/search               — 多维度关键字搜索(P0)</li>
 *   <li>GET  /api/secret/history/list         — 历史版本列表(P0)</li>
 *   <li>POST /api/secret/rollback             — 回滚到指定版本(P0)</li>
 *   <li>POST /api/secret/rotate               — 手动触发轮换(P1)</li>
 *   <li>POST /api/secret/dynamic/generate     — 生成动态密钥(P1)</li>
 *   <li>GET  /api/secret/dynamic/{dynKey}     — 读取动态密钥(P1)</li>
 *   <li>DELETE /api/secret/dynamic/{dynKey}   — 主动撤销(P1)</li>
 * </ul>
 */
@Tag(name = "密钥操作(FEATURE026)")
@RestController
@RequestMapping("/api/secret")
public class SecretOperationController {

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "获取密钥明文")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/plain")
    public Map<String, Object> getPlain(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            @RequestParam(required = false) String cipherText,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            String plain = secretService.decryptToPlain(secretKey, group, namespace, cipherText);
            result.put("success", true);
            result.put("data", plain);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(secretKey, group, namespace, "GET_PLAIN",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "关键字搜索密钥")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            List<ZMistSecretInfo> list = secretService.searchSecrets(keyword, group, namespace);
            result.put("success", true);
            result.put("data", list);
            result.put("total", list.size());
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(null, group, namespace, "SEARCH",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "查询历史版本")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/history/list")
    public Map<String, Object> historyList(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            List<ZMistSecretHistory> list = secretService.listHistory(secretKey, group, namespace);
            result.put("success", true);
            result.put("data", list);
            result.put("total", list.size());
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(secretKey, group, namespace, "HISTORY",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "回滚到指定历史版本")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/rollback")
    public Map<String, Object> rollback(
            @RequestParam Long historyId,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            ZMistSecretInfo secret = secretService.rollbackToHistory(historyId);
            result.put("success", true);
            result.put("data", secret);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(null, null, null, "ROLLBACK",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "手动触发密钥轮换")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/rotate")
    public Map<String, Object> rotate(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            @RequestParam(required = false, defaultValue = "32") Integer newValueLength,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            ZMistSecretInfo secret = secretService.rotateNow(secretKey, group, namespace, newValueLength);
            result.put("success", true);
            result.put("data", secret);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(secretKey, group, namespace, "ROTATE",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "生成动态密钥(带TTL)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @PostMapping("/dynamic/generate")
    public Map<String, Object> generateDynamic(
            @RequestParam(required = false) String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            @RequestParam(required = false, defaultValue = "3600") int ttlSeconds,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            String dynKey = secretService.generateDynamic(secretKey, group, namespace, ttlSeconds,
                    currentOperator(request));
            result.put("success", true);
            result.put("data", dynKey);
            result.put("ttlSeconds", ttlSeconds);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(secretKey, group, namespace, "DYN_GENERATE",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "读取动态密钥明文")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/dynamic/{dynKey}")
    public Map<String, Object> readDynamic(@PathVariable String dynKey, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            String plain = secretService.readDynamic(dynKey);
            result.put("success", true);
            result.put("data", plain);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(null, null, null, "DYN_READ",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    @Operation(summary = "主动撤销动态密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/dynamic/{dynKey}")
    public Map<String, Object> revokeDynamic(@PathVariable String dynKey, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String errMsg = null;
        try {
            boolean revoked = secretService.revokeDynamic(dynKey);
            result.put("success", revoked);
            result.put("message", revoked ? "撤销成功" : "撤销失败(可能不存在或已撤销)");
            success = revoked;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errMsg = e.getMessage();
        } finally {
            secretService.recordAccess(null, null, null, "DYN_REVOKE",
                    currentOperator(request), currentIp(request), success, errMsg);
        }
        return result;
    }

    private String currentOperator(HttpServletRequest request) {
        String xff = request.getHeader("X-Staff-No");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.trim();
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
}
