package com.zifang.z.mist.core.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.mist.core.domain.entity.ZMistSecretAccessLog;

/**
 * 密钥访问日志 Mapper
 * <p>
 * FEATURE021 引入。每次 secret 操作都会通过此 mapper 写一行。
 *
 * @author zifang
 * @see ZMistSecretAccessLog
 * @since 1.0.0
 */
public interface ZMistSecretAccessLogMapper extends BaseMapper<ZMistSecretAccessLog> {
}
