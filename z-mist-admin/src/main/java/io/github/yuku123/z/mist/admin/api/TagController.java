package io.github.yuku123.z.mist.admin.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretTag;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistSecretTagMapper;
import io.github.yuku123.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 密钥标签管理(FEATURE026 P1 + FEATURE065 审计增强).
 * <p>
 * 支持多维度标签: env=prod, team=infra, project=trade。
 * 通过标签可批量查找密钥(看板/告警依赖)。
 * <p>
 * FEATURE065: 标签变更操作记录到 access_log(op_type=TAG_ADD/TAG_DELETE)
 *
 * <ul>
 *   <li>POST   /api/tag              — 添加标签(upsert)</li>
 *   <li>DELETE /api/tag/{id}         — 删除标签</li>
 *   <li>GET    /api/tag/by-secret    — 查询某密钥的所有标签</li>
 *   <li>GET    /api/tag/search       — 按标签搜索密钥</li>
 * </ul>
 */
@Tag(name = "标签管理(FEATURE026)")
@RestController
@RequestMapping("/api/tag")
public class TagController {

    @Autowired
    private ZMistSecretTagMapper tagMapper;

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "添加标签(upsert)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Map<String, Object> add(@RequestBody ZMistSecretTag body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (body.getSecretKey() == null || body.getTagKey() == null) {
            result.put("success", false);
            result.put("message", "secretKey 与 tagKey 必填");
            return result;
        }
        if (body.getGroup() == null) {
            body.setGroup("DEFAULT_GROUP");
        }
        if (body.getNamespace() == null) {
            body.setNamespace("");
        }
        // upsert: 如果已存在,更新 value
        LambdaQueryWrapper<ZMistSecretTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretTag::getSecretKey, body.getSecretKey())
                .eq(ZMistSecretTag::getGroup, body.getGroup())
                .eq(ZMistSecretTag::getNamespace, body.getNamespace())
                .eq(ZMistSecretTag::getTagKey, body.getTagKey());
        ZMistSecretTag existing = tagMapper.selectOne(wrapper);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setTagValue(body.getTagValue());
            existing.setGmtModified(now);
            tagMapper.updateById(existing);
            result.put("data", existing);
        } else {
            body.setGmtCreate(now);
            body.setGmtModified(now);
            tagMapper.insert(body);
            result.put("data", body);
        }
        result.put("success", true);
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "TAG_ADD", currentOperator(request), currentIp(request), true,
                "key=" + body.getTagKey() + ",value=" + body.getTagValue());
        return result;
    }

    @Operation(summary = "删除标签")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        ZMistSecretTag tag = tagMapper.selectById(id);
        int rows = tagMapper.deleteById(id);
        result.put("success", rows > 0);
        // FEATURE065: 审计
        if (tag != null) {
            secretService.recordAccess(tag.getSecretKey(), tag.getGroup(), tag.getNamespace(),
                    "TAG_DELETE", currentOperator(request), currentIp(request), rows > 0,
                    rows > 0 ? null : "delete_failed");
        }
        return result;
    }

    @Operation(summary = "查询某密钥的所有标签")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/by-secret")
    public Map<String, Object> listBySecret(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<ZMistSecretTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretTag::getSecretKey, secretKey)
                .eq(ZMistSecretTag::getGroup, group)
                .eq(ZMistSecretTag::getNamespace, namespace);
        List<ZMistSecretTag> tags = tagMapper.selectList(wrapper);
        result.put("success", true);
        result.put("data", tags);
        result.put("total", tags.size());
        return result;
    }

    @Operation(summary = "按 tagKey/tagValue 搜索密钥")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String tagKey,
            @RequestParam(required = false) String tagValue) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<ZMistSecretTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretTag::getTagKey, tagKey);
        if (tagValue != null && !tagValue.isEmpty()) {
            wrapper.eq(ZMistSecretTag::getTagValue, tagValue);
        }
        List<ZMistSecretTag> tags = tagMapper.selectList(wrapper);
        result.put("success", true);
        result.put("data", tags);
        result.put("total", tags.size());
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
}
