package com.zifang.z.mist.common;

/**
 * 常量定义
 */
public class Constance {

    public static final String DEFAULT_NAMESPACE = "";

    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    public static final String DEFAULT_CLUSTER = "DEFAULT";

    /**
     * 密钥类型
     */
    public static class SecretType {
        public static final String TEXT = "text";
        public static final String PASSWORD = "password";
        public static final String CERT = "cert";
        public static final String KEY = "key";
        public static final String API_KEY = "api_key";
    }

    /**
     * 加密算法
     */
    public static class EncryptAlgorithm {
        public static final String AES = "AES";
        public static final String RSA = "RSA";
    }

    /**
     * 权限级别
     */
    public static class PermissionLevel {
        public static final String READ = "read";
        public static final String DECRYPT = "decrypt";
    }

    /**
     * 环境
     */
    public static class Env {
        public static final String DEV = "dev";
        public static final String TEST = "test";
        public static final String PRE = "pre";
        public static final String PROD = "prod";
    }
}