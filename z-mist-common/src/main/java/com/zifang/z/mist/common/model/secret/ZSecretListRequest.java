package com.zifang.z.mist.common.model.secret;

import java.util.List;

/**
 * 密钥列表请求
 */
public class ZSecretListRequest {

    private List<String> secretKeys;
    private String group;
    private String namespace;

    public List<String> getSecretKeys() {
        return secretKeys;
    }

    public void setSecretKeys(List<String> secretKeys) {
        this.secretKeys = secretKeys;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}