package com.zifang.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.mist.common.Result;
import com.zifang.z.mist.core.domain.entity.ZMistSecretAcl;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretAclMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import com.zifang.z.mist.web.api.request.AclReq;
import com.zifang.z.mist.web.api.response.AclResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 密钥授权管理(FEATURE026 P1 + FEATURE065 审计增强).
 * <p>
 * 对应 z_mist_secret_acl 表的 CRUD。
 * 每条 ACL 记录一个 (secretKey, group, namespace, authorizedApp, authorizedEnv)
 * 五元组的授权关系。
 * <p>
 * FEATURE065: 所有授权变更操作记录到 access_log(op_type=ACL_CREATE/ACL_UPDATE/ACL_DELETE)
 *
 * <ul>
 *   <li>POST   /api/acl                  — 创建授权</li>
 *   <li>PUT    /api/acl/{id}             — 更新授权</li>
 *   <li>DELETE /api/acl/{id}             — 删除授权</li>
 *   <li>GET    /api/acl/page             — 分页查询</li>
 *   <li>GET    /api/acl/check            — 校验授权</li>
 * </ul>
 */
@Tag(name = "授权管理(FEATURE026)")
@RestController("zMistAclController")
@RequestMapping("/api/acl")
public class AclController {

    @Autowired
    private ZMistSecretAclMapper aclMapper;

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "创建授权")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Result<AclResp> create(@RequestBody AclReq aclReq, HttpServletRequest request) {
        ZMistSecretAcl body = new ZMistSecretAcl();
        BeanUtils.copyProperties(aclReq, body);
        Result<AclResp> result = new Result<>();
        if (body.getSecretKey() == null || body.getAuthorizedApp() == null) {
            result.setSuccess(false);
            result.setMessage("secretKey 与 authorizedApp 必填");
            return result;
        }
        if (body.getPermissionLevel() == null) {
            body.setPermissionLevel("read");
        }
        if (body.getGroup() == null) {
            body.setGroup("DEFAULT_GROUP");
        }
        if (body.getNamespace() == null) {
            body.setNamespace("");
        }
        LocalDateTime now = LocalDateTime.now();
        body.setGmtCreate(now);
        body.setGmtModified(now);
        aclMapper.insert(body);
        result.setSuccess(true);
        result.setData(toResp(body));
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "ACL_CREATE", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    @Operation(summary = "更新授权")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PutMapping("/{id}")
    public Result<AclResp> update(@PathVariable Long id, @RequestBody AclReq aclReq, HttpServletRequest request) {
        ZMistSecretAcl body = new ZMistSecretAcl();
        BeanUtils.copyProperties(aclReq, body);
        Result<AclResp> result = new Result<>();
        body.setId(id);
        body.setGmtModified(LocalDateTime.now());
        aclMapper.updateById(body);
        result.setSuccess(true);
        ZMistSecretAcl saved = aclMapper.selectById(id);
        result.setData(saved == null ? null : toResp(saved));
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "ACL_UPDATE", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    @Operation(summary = "删除授权")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Result<Void> result = new Result<>();
        ZMistSecretAcl acl = aclMapper.selectById(id);
        int rows = aclMapper.deleteById(id);
        result.setSuccess(rows > 0);
        result.setMessage(rows > 0 ? "删除成功" : "授权不存在");
        // FEATURE065: 审计
        if (acl != null) {
            secretService.recordAccess(acl.getSecretKey(), acl.getGroup(), acl.getNamespace(),
                    "ACL_DELETE", currentOperator(request), currentIp(request), rows > 0,
                    rows > 0 ? null : "delete_failed");
        }
        return result;
    }

    @Operation(summary = "分页查询授权")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String secretKey,
            @RequestParam(required = false) String authorizedApp,
            @RequestParam(required = false) String authorizedEnv,
            @RequestParam(required = false) String namespace) {
        // 不规则聚合输出(total 自定义 key), 保持原状
        Map<String, Object> result = new HashMap<>();
        Page<ZMistSecretAcl> page = new Page<>(current, size);
        LambdaQueryWrapper<ZMistSecretAcl> wrapper = new LambdaQueryWrapper<>();
        if (secretKey != null && !secretKey.isEmpty()) {
            wrapper.eq(ZMistSecretAcl::getSecretKey, secretKey);
        }
        if (authorizedApp != null && !authorizedApp.isEmpty()) {
            wrapper.eq(ZMistSecretAcl::getAuthorizedApp, authorizedApp);
        }
        if (authorizedEnv != null && !authorizedEnv.isEmpty()) {
            wrapper.eq(ZMistSecretAcl::getAuthorizedEnv, authorizedEnv);
        }
        if (namespace != null && !namespace.isEmpty()) {
            wrapper.eq(ZMistSecretAcl::getNamespace, namespace);
        }
        wrapper.orderByDesc(ZMistSecretAcl::getGmtCreate);
        Page<ZMistSecretAcl> p = aclMapper.selectPage(page, wrapper);
        result.put("success", true);
        result.put("data", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    @Operation(summary = "校验应用是否被授权访问密钥")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/check")
    public Map<String, Object> check(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            @RequestParam String authorizedApp,
            @RequestParam(required = false) String authorizedEnv) {
        // 不规则聚合输出(authorized/permissionLevel 自定义 key), 保持原状
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<ZMistSecretAcl> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretAcl::getSecretKey, secretKey)
                .eq(ZMistSecretAcl::getGroup, group)
                .eq(ZMistSecretAcl::getNamespace, namespace)
                .eq(ZMistSecretAcl::getAuthorizedApp, authorizedApp);
        if (authorizedEnv != null && !authorizedEnv.isEmpty()) {
            wrapper.eq(ZMistSecretAcl::getAuthorizedEnv, authorizedEnv);
        }
        ZMistSecretAcl acl = aclMapper.selectOne(wrapper);
        boolean authorized = acl != null && (acl.getExpireTime() == null || acl.getExpireTime().isAfter(LocalDateTime.now()));
        result.put("success", true);
        result.put("authorized", authorized);
        result.put("permissionLevel", authorized ? acl.getPermissionLevel() : null);
        return result;
    }

    private String currentOperator(HttpServletRequest request) {
        String xff = request.getHeader("X-Staff-No");
        return (xff != null && !xff.isEmpty()) ? xff : "anonymous";
    }

    private String currentIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private AclResp toResp(ZMistSecretAcl acl) {
        AclResp resp = new AclResp();
        BeanUtils.copyProperties(acl, resp);
        return resp;
    }
}
