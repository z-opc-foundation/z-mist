package com.zifang.z.mist.admin.api.response;

import java.io.Serializable;

/**
 * 重置 appSecret 响应 DTO(原 AppController reset-secret 端点内联 data Map 下沉为 DTO).
 *
 * <p>JSON 字段名与历史完全一致: id / appName / newSecret。
 *
 * @author zifang
 * @since 1.0.0
 */
public class ResetSecretResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String appName;
    private String newSecret;

    public ResetSecretResp() {
    }

    public ResetSecretResp(Long id, String appName, String newSecret) {
        this.id = id;
        this.appName = appName;
        this.newSecret = newSecret;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getNewSecret() { return newSecret; }
    public void setNewSecret(String newSecret) { this.newSecret = newSecret; }
}
