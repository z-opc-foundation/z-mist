package com.zifang.z.mist.web.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器.
 * <p>
 * API 基础路径: /api/mist
 * 所属模块: z-mist-admin
 * 鉴权: 无需鉴权,用于监控系统存活探测
 *
 * <p>主要端点:
 * <ul>
 *   <li>GET /api/mist/health — 返回服务健康状态</li>
 * </ul>
 */
@RestController
public class MistBaseHealthController {

    /**
     * 返回服务健康状态.
     *
     * @return 包含 status(UP) 与 service(z-mist-admin) 的健康信息映射
     */
    @GetMapping("/api/mist/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "z-mist-admin");
        return result;
    }
}