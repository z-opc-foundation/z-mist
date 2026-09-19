package com.zifang.z.mist.examples;

import com.zifang.z.mist.client.config.MistClient;

/**
 * z-schedule 执行器启动时，从 z-mist 预加载密钥的参考实现。
 * <p>
 * 用法：在 z-schedule 执行器项目的 Application 类 main() 里加：
 * <pre>
 *   // 注入 MistClient（通过 z-mist-spring-boot-starter 自动装配）
 *   MistClient mist = SpringApplication.run(...).getBean(MistClient.class);
 *   SecretsPreloader.preload(mist, new String[]{
 *       "db_password", "redis_password", "wechat_app_secret"
 *   }, "prod");
 * </pre>
 * <p>
 * 然后业务里用 {@code SecretsPreloader.get("db_password")} 拿明文，
 * 启动期一次拉取，运行期零网络开销。
 *
 * <h3>FEATURE022 reference impl</h3>
 * 这不是 z-mist 核心代码，是给"业务项目怎么用 z-mist"提供模板。
 * 真业务项目应自行修改（线程安全策略、过期刷新、内存上限等）。
 */
public final class SecretsPreloader {

    private static final java.util.Map<String, String> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private SecretsPreloader() {
    }

    /**
     * 启动期同步拉取一批密钥到内存。
     *
     * @param mist       MistClient 实例
     * @param secretKeys 要预加载的密钥标识列表
     * @param namespace  命名空间 (dev/staging/prod)
     */
    public static void preload(MistClient mist, String[] secretKeys, String namespace) {
        for (String key : secretKeys) {
            try {
                String plain = mist.getSecret(key, "DEFAULT_GROUP", namespace);
                if (plain != null) {
                    CACHE.put(namespace + ":" + key, plain);
                }
            } catch (Exception e) {
                // 启动期失败要 noisy —— 业务用不存在的密钥会跑挂
                throw new RuntimeException("Failed to preload secret " + key + " in " + namespace, e);
            }
        }
    }

    /**
     * 拿已预加载的明文。返回 null 表示没预加载过。
     */
    public static String get(String secretKey) {
        return CACHE.get(secretKey);
    }

    public static String get(String secretKey, String namespace) {
        return CACHE.get(namespace + ":" + secretKey);
    }

    /**
     * 强制刷新某个密钥（用于轮换场景）。
     */
    public static void refresh(MistClient mist, String secretKey, String namespace) {
        String plain = mist.getSecret(secretKey, "DEFAULT_GROUP", namespace);
        CACHE.put(namespace + ":" + secretKey, plain);
    }

    public static int size() {
        return CACHE.size();
    }

    public static void clear() {
        CACHE.clear();
    }
}
