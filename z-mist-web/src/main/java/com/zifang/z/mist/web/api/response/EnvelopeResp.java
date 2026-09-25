package com.zifang.z.mist.web.api.response;

import java.io.Serializable;

/**
 * 信封加密响应 DTO(原 MasterKeyController envelope 端点内联 data Map 下沉为 DTO).
 *
 * <p>JSON 字段名与历史完全一致: wrappedKey / cipherText / algorithm。
 *
 * @author zifang
 * @since 1.0.0
 */
public class EnvelopeResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String wrappedKey;
    private String cipherText;
    private String algorithm;

    public EnvelopeResp() {
    }

    public EnvelopeResp(String wrappedKey, String cipherText, String algorithm) {
        this.wrappedKey = wrappedKey;
        this.cipherText = cipherText;
        this.algorithm = algorithm;
    }

    public String getWrappedKey() { return wrappedKey; }
    public void setWrappedKey(String wrappedKey) { this.wrappedKey = wrappedKey; }
    public String getCipherText() { return cipherText; }
    public void setCipherText(String cipherText) { this.cipherText = cipherText; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
}
