package com.zifang.z.mist.core.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zifang.z.mist.core.domain.entity.ZMistSecretHistory;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;

import java.util.List;

/**
 * 密钥服务接口
 *
 * @author zifang
 * @see ZMistSecretInfo
 * @see ZMistSecretHistory
 * @since 1.0.0
 */
public interface IZMistSecretService extends IService<ZMistSecretInfo> {

    /**
     * 保存密钥
     *
     * @param secret 密钥信息
     * @return 保存后的密钥信息
     */
    ZMistSecretInfo saveSecret(ZMistSecretInfo secret);

    /**
     * 更新密钥
     *
     * @param secret 密钥信息
     * @return 更新后的密钥信息
     */
    ZMistSecretInfo updateSecret(ZMistSecretInfo secret);

    /**
     * 删除密钥
     *
     * @param secretKey 密钥Key
     * @param group     分组
     * @param namespace 命名空间
     * @return 是否删除成功
     */
    boolean deleteSecret(String secretKey, String group, String namespace);

    /**
     * 根据 key 查询密钥
     *
     * @param secretKey 密钥Key
     * @param group     分组
     * @param namespace 命名空间
     * @return 密钥信息,不存在则返回null
     */
    ZMistSecretInfo getSecret(String secretKey, String group, String namespace);

    /**
     * 查询密钥列表
     *
     * @param group     分组
     * @param appName   应用名
     * @param namespace 命名空间
     * @return 密钥列表
     */
    List<ZMistSecretInfo> listSecrets(String group, String appName, String namespace);

    /**
     * 加密密钥值
     *
     * @param plainValue 明文值
     * @param algorithm  加密算法
     * @return 加密后的密文
     * @throws RuntimeException 加密失败时抛出
     */
    String encryptValue(String plainValue, String algorithm);

    /**
     * 解密密钥值
     *
     * @param encryptedValue 密文值
     * @param algorithm      加密算法
     * @return 解密后的明文
     * @throws RuntimeException 解密失败时抛出
     */
    String decryptValue(String encryptedValue, String algorithm);

    /**
     * 解密指定密钥的密文得到明文(FEATURE026 P0).
     * <p>
     * 用于业务方拿到 ciphertext 后回查明文(同主密钥可双向解析)。
     *
     * @param secretKey  密钥标识
     * @param group      分组
     * @param namespace  命名空间
     * @param cipherText Base64 密文,若传 null 则取数据库存的密文
     * @return 明文
     * @throws IllegalArgumentException 密钥不存在或密文为空时抛出
     */
    String decryptToPlain(String secretKey, String group, String namespace, String cipherText);

    /**
     * 加密即服务:对任意明文按指定算法加密,不落库(FEATURE026 P0).
     *
     * @param plainText 明文
     * @param algorithm 算法(AES/RSA)
     * @return Base64 密文
     * @throws IllegalArgumentException 当 plainText 为 null 时抛出
     */
    String eaaSEncrypt(String plainText, String algorithm);

    /**
     * 加密即服务:对密文解密,不落库(FEATURE026 P0).
     *
     * @param cipherText Base64 密文
     * @param algorithm  算法
     * @return 明文
     * @throws IllegalArgumentException 当 cipherText 为 null 时抛出
     */
    String eaaSDecrypt(String cipherText, String algorithm);

    /**
     * 多维度关键字搜索密钥(FEATURE026 P0).
     *
     * @param keyword   关键字(匹配 secretKey/secretName/description)
     * @param group     分组
     * @param namespace 命名空间
     * @return 命中列表
     */
    List<ZMistSecretInfo> searchSecrets(String keyword, String group, String namespace);

    /**
     * 查询密钥的历史版本快照(FEATURE026 P0).
     *
     * @param secretKey 密钥标识
     * @param group     分组
     * @param namespace 命名空间
     * @return 历史版本列表(最新在前)
     */
    List<ZMistSecretHistory> listHistory(String secretKey, String group, String namespace);

    /**
     * 把密钥回滚到指定历史版本(FEATURE026 P0).
     *
     * @param historyId z_mist_secret_history.id
     * @return 回滚后的最新密钥
     * @throws IllegalArgumentException historyId 为空或历史记录不存在时抛出
     */
    ZMistSecretInfo rollbackToHistory(Long historyId);

    /**
     * 触发一次手动密钥轮换:重新生成随机值并自增版本(FEATURE026 P1).
     *
     * @param secretKey      密钥标识
     * @param group          分组
     * @param namespace      命名空间
     * @param newValueLength 新值长度
     * @return 轮换后的密钥
     * @throws IllegalArgumentException secret 不存在时抛出
     */
    ZMistSecretInfo rotateNow(String secretKey, String group, String namespace, Integer newValueLength);

    /**
     * 生成动态密钥(带 TTL,过期自动失效;FEATURE026 P1).
     *
     * @param secretKey  关联的原始密钥(若为空则生成全新随机)
     * @param group      分组
     * @param namespace  命名空间
     * @param ttlSeconds TTL 秒数
     * @param creator    创建者
     * @return dynKey 动态密钥标识
     * @throws IllegalArgumentException 源密钥不存在时抛出
     */
    String generateDynamic(String secretKey, String group, String namespace, int ttlSeconds, String creator);

    /**
     * 读取并校验动态密钥(FEATURE026 P1).
     *
     * @param dynKey 动态密钥标识
     * @return 明文(若已过期/已撤销则抛异常)
     * @throws IllegalArgumentException dynKey 为空或密钥不存在时抛出
     * @throws IllegalStateException    密钥已过期或已撤销时抛出
     */
    String readDynamic(String dynKey);

    /**
     * 主动撤销动态密钥(FEATURE026 P1).
     *
     * @param dynKey 动态密钥标识
     * @return 是否成功
     */
    boolean revokeDynamic(String dynKey);

    /**
     * 记录密钥访问日志（FEATURE021）
     * <p>
     * 每次 secret_get / secret_put / secret_delete / secret_list 都会调用。
     * 失败不应抛出异常 —— 审计日志失败不能影响业务。
     *
     * @param secretKey    密钥标识（删除/获取场景可能为 null）
     * @param group        分组
     * @param namespace    命名空间
     * @param opType       操作类型: GET / PUT / DELETE / LIST
     * @param operator     操作者（z-ctc username）
     * @param operatorIp   操作者 IP
     * @param success      是否成功
     * @param errorMessage 失败时的错误信息
     */
    void recordAccess(String secretKey, String group, String namespace,
                      String opType, String operator, String operatorIp,
                      boolean success, String errorMessage);
}