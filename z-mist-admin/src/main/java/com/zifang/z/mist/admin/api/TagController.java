package com.zifang.z.mist.admin.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.common.Result;
import com.zifang.z.mist.core.domain.entity.ZMistSecretTag;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretTagMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import com.zifang.z.mist.admin.api.request.TagReq;
import com.zifang.z.mist.admin.api.response.TagResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
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
@RestController("zMistTagController")
@RequestMapping("/api/tag")
public class TagController {

    @Autowired
    private ZMistSecretTagMapper tagMapper;

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "添加标签(upsert)")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Result<TagResp> add(@RequestBody TagReq tagReq, HttpServletRequest request) {
        ZMistSecretTag body = new ZMistSecretTag();
        BeanUtils.copyProperties(tagReq, body);
        Result<TagResp> result = new Result<>();
        if (body.getSecretKey() == null || body.getTagKey() == null) {
            result.setSuccess(false);
            result.setMessage("secretKey 与 tagKey 必填");
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
            result.setData(toResp(existing));
        } else {
            body.setGmtCreate(now);
            body.setGmtModified(now);
            tagMapper.insert(body);
            result.setData(toResp(body));
        }
        result.setSuccess(true);
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "TAG_ADD", currentOperator(request), currentIp(request), true,
                "key=" + body.getTagKey() + ",value=" + body.getTagValue());
        return result;
    }

    @Operation(summary = "删除标签")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Result<Void> result = new Result<>();
        ZMistSecretTag tag = tagMapper.selectById(id);
        int rows = tagMapper.deleteById(id);
        result.setSuccess(rows > 0);
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
        // 不规则聚合输出(total 自定义 key), 保持原状
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
        // 不规则聚合输出(total 自定义 key), 保持原状
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

    private TagResp toResp(ZMistSecretTag tag) {
        TagResp resp = new TagResp();
        BeanUtils.copyProperties(tag, resp);
        return resp;
    }
}
