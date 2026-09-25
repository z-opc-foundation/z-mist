package com.zifang.z.mist.web.api.request;

import java.io.Serializable;

/**
 * EaaS 请求体: 加密时 plainText 必填, 解密时 cipherText 必填.
 * <p>
 * 原 {@code EaaSController.EaaSRequest} 内部静态类下沉到 request 包。
 * JSON 字段名与历史完全一致: plainText / cipherText / algorithm。
 *
 * @author zifang
 * @since 1.0.0
 */
public class EaaSRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public String plainText;
    public String cipherText;
    public String algorithm;
}
