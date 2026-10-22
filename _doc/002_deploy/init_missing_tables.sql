-- ============================================================================
-- z-mist 缺表补齐脚本（只建表，不删数据）
-- ----------------------------------------------------------------------------
-- 用途：把库里缺的 8 张 z-mist 表建出来。
--      这 8 张表压着 12 个只读端点（11 个返 500，/api/secret/dynamic/{k} 返 200
--      但 body 里带整段裸 SQL 错误），并且让访问审计（z_mist_secret_access_log）
--      真的能落库 —— 缺表时密钥访问轨迹一条都没记下来，而接口照样 200、页面照样全绿。
--
-- ⚠️ 本脚本与 init.sql 的区别（这就是本脚本存在的全部理由）：
--      init.sql 里 15 条 `DROP TABLE IF EXISTS` 与 15 条 `CREATE TABLE IF NOT EXISTS`
--      一一对应、同名同集合 ⇒「照 init.sql 跑一遍」= 先把 z_mist_secret_info 这类
--      已有真数据的表删掉再重建（= 清库）。共享库上不能这么干。
--      本脚本只有 `CREATE TABLE IF NOT EXISTS`：没有 DROP / ALTER / TRUNCATE / DELETE，
--      对已存在的表是 no-op，对不存在的表才建。
--      下面的建表语句是从 init.sql 原文逐字摘出来的（未做任何改写），
--      保证与 z-mist-core 的实体/Mapper 期望的列完全一致。
--
-- 用法：
--   1) 先自己确认目标库（共享库是 `oc`，独立部署是 `z_mist`）——本文件不含
--      CREATE DATABASE / USE，执行前必须自己切库：
--        mysql -h<host> -P<port> -u<user> -p -e "USE oc; SELECT DATABASE();"
--   2) 再喂本文件：
--        mysql -h<host> -P<port> -u<user> -p oc < init_missing_tables.sql
--
-- 本脚本不灌种子数据：8 张表的种子要不要一起灌由人决定（见文末「可选：种子数据」）。
--
-- 执行后自检（z-mist 表数应当是 15，且与 init.sql 的 15 张同名同集合）：
--   SELECT COUNT(*) FROM information_schema.tables
--     WHERE table_schema = DATABASE() AND table_name LIKE 'z\_mist%';
--   SELECT table_name FROM information_schema.tables
--     WHERE table_schema = DATABASE() AND table_name LIKE 'z\_mist%' ORDER BY table_name;
-- ============================================================================

CREATE TABLE IF NOT EXISTS `z_mist_secret_access_log`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `secret_key` varchar
(
    255
) DEFAULT NULL COMMENT '密钥标识',
    `group` varchar
(
    128
) DEFAULT NULL COMMENT '密钥分组',
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '命名空间',
    `op_type` varchar
(
    32
) NOT NULL COMMENT '操作类型: GET / PUT / DELETE / LIST',
    `operator` varchar
(
    128
) DEFAULT 'anonymous' COMMENT '操作者(z-ctc username)',
    `operator_ip` varchar
(
    64
) DEFAULT NULL COMMENT '操作者 IP',
    `success` tinyint
(
    1
) DEFAULT 1 COMMENT '是否成功(1=成功, 0=失败)',
    `error_message` varchar
(
    1024
) DEFAULT NULL COMMENT '失败时的错误信息',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY
(
    `id`
),
    KEY `idx_secret_key`
(
    `secret_key`
),
    KEY `idx_operator`
(
    `operator`
),
    KEY `idx_op_type`
(
    `op_type`
),
    KEY `idx_gmt_create`
(
    `gmt_create`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='密钥访问日志表(FEATURE021)';

-- ============================================================
-- FEATURE026: z-mist 2.0 全功能增强 — 新增 7 张表
-- ============================================================

-- 1. 密钥标签表
CREATE TABLE IF NOT EXISTS `z_mist_secret_tag`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `secret_key` varchar
(
    255
) NOT NULL COMMENT '密钥标识',
    `group` varchar
(
    128
) DEFAULT NULL COMMENT '密钥分组',
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '命名空间',
    `tag_key` varchar
(
    64
) NOT NULL COMMENT '标签Key(env=prod/team=infra)',
    `tag_value` varchar
(
    255
) DEFAULT NULL COMMENT '标签Value',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` datetime NOT NULL COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    KEY `idx_secret`
(
    `secret_key`,
    `group`,
    `namespace`
),
    KEY `idx_tag_key`
(
    `tag_key`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='密钥标签表(FEATURE026)';

-- 2. 自动密钥轮换策略表
CREATE TABLE IF NOT EXISTS `z_mist_secret_rotation_policy`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `secret_key` varchar
(
    255
) NOT NULL COMMENT '密钥标识',
    `group` varchar
(
    128
) DEFAULT NULL,
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '命名空间',
    `cron_expression` varchar
(
    64
) NOT NULL COMMENT 'cron表达式(0 0 0 * * ? 每日0点)',
    `rotation_strategy` varchar
(
    32
) DEFAULT 'auto' COMMENT '轮换策略:auto=自动生成,manual=调用API触发',
    `new_value_length` int
(
    11
) DEFAULT 32 COMMENT '新密钥值长度',
    `enabled` tinyint
(
    1
) DEFAULT 1 COMMENT '是否启用',
    `last_rotation_time` datetime DEFAULT NULL COMMENT '上次轮换时间',
    `next_rotation_time` datetime DEFAULT NULL COMMENT '下次轮换时间',
    `description` varchar
(
    512
) DEFAULT NULL,
    `creator_staff_no` varchar
(
    255
) DEFAULT NULL,
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` datetime NOT NULL,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_secret_group_ns`
(
    `secret_key`,
    `group`,
    `namespace`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='密钥自动轮换策略表(FEATURE026)';

-- 3. 密钥轮换历史表
CREATE TABLE IF NOT EXISTS `z_mist_secret_rotation_history`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `policy_id` bigint
(
    20
) DEFAULT NULL COMMENT '轮换策略ID',
    `secret_key` varchar
(
    255
) NOT NULL,
    `group` varchar
(
    128
) DEFAULT NULL,
    `namespace` varchar
(
    128
) DEFAULT '',
    `old_version` varchar
(
    32
) DEFAULT NULL COMMENT '旧版本号',
    `new_version` varchar
(
    32
) DEFAULT NULL COMMENT '新版本号',
    `trigger_type` varchar
(
    32
) DEFAULT NULL COMMENT '触发类型:cron/manual/api',
    `trigger_by` varchar
(
    255
) DEFAULT NULL COMMENT '触发者',
    `success` tinyint
(
    1
) DEFAULT 1,
    `error_message` varchar
(
    1024
) DEFAULT NULL,
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    KEY `idx_secret`
(
    `secret_key`,
    `group`,
    `namespace`
),
    KEY `idx_gmt_create`
(
    `gmt_create`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='密钥轮换历史表(FEATURE026)';

-- 4. 动态密钥表(带TTL,过期自动失效)
CREATE TABLE IF NOT EXISTS `z_mist_secret_dynamic`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dyn_key` varchar
(
    128
) NOT NULL COMMENT '动态密钥标识(uuid)',
    `secret_key` varchar
(
    255
) DEFAULT NULL COMMENT '关联的原始密钥',
    `group` varchar
(
    128
) DEFAULT NULL,
    `namespace` varchar
(
    128
) DEFAULT '',
    `encrypted_value` longtext NOT NULL COMMENT '加密后的密钥值',
    `algorithm` varchar
(
    32
) DEFAULT 'AES',
    `lease_id` varchar
(
    128
) DEFAULT NULL COMMENT '租约ID',
    `ttl_seconds` int
(
    11
) DEFAULT 3600 COMMENT '过期秒数',
    `expire_time` datetime NOT NULL COMMENT '过期时间',
    `revoked` tinyint
(
    1
) DEFAULT 0 COMMENT '是否撤销',
    `revoke_time` datetime DEFAULT NULL,
    `creator` varchar
(
    128
) DEFAULT 'anonymous',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_dyn_key`
(
    `dyn_key`
),
    KEY `idx_expire`
(
    `expire_time`
),
    KEY `idx_secret`
(
    `secret_key`,
    `group`,
    `namespace`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='动态密钥表(FEATURE026,带TTL自动过期)';

-- 5. 主密钥历史表(支持主密钥轮换re-encrypt)
CREATE TABLE IF NOT EXISTS `z_mist_master_key_history`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `key_alias` varchar
(
    128
) NOT NULL COMMENT '密钥别名(mist-default)',
    `key_version` varchar
(
    32
) NOT NULL COMMENT '版本号(v1/v2)',
    `key_md5` varchar
(
    32
) NOT NULL COMMENT '主密钥MD5指纹',
    `algorithm` varchar
(
    32
) DEFAULT 'AES' COMMENT '加密算法',
    `enabled` tinyint
(
    1
) DEFAULT 1 COMMENT '是否启用',
    `activated_time` datetime DEFAULT NULL COMMENT '启用时间',
    `deactivated_time` datetime DEFAULT NULL COMMENT '停用时间',
    `creator` varchar
(
    255
) DEFAULT NULL,
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_alias_version`
(
    `key_alias`,
    `key_version`
),
    KEY `idx_enabled`
(
    `enabled`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='主密钥历史表(FEATURE026,支持re-encrypt)';

-- 6. 通知日志表(过期告警)
CREATE TABLE IF NOT EXISTS `z_mist_notification_log`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `notify_type` varchar
(
    32
) NOT NULL COMMENT '通知类型:expiring/expired/rotation_failed',
    `secret_key` varchar
(
    255
) DEFAULT NULL,
    `group` varchar
(
    128
) DEFAULT NULL,
    `namespace` varchar
(
    128
) DEFAULT '',
    `channel` varchar
(
    32
) DEFAULT 'log' COMMENT '通知渠道:log/webhook/email',
    `target` varchar
(
    512
) DEFAULT NULL COMMENT '通知目标地址',
    `payload` text COMMENT '通知内容',
    `success` tinyint
(
    1
) DEFAULT 1,
    `error_message` varchar
(
    1024
) DEFAULT NULL,
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    KEY `idx_secret`
(
    `secret_key`
),
    KEY `idx_gmt_create`
(
    `gmt_create`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通知发送日志(FEATURE026)';

-- 7. 每日统计表(数据看板)
CREATE TABLE IF NOT EXISTS `z_mist_stats_daily`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date` date NOT NULL COMMENT '统计日期',
    `secret_count` int
(
    11
) DEFAULT 0 COMMENT '当日密钥总数',
    `new_count` int
(
    11
) DEFAULT 0 COMMENT '当日新增',
    `update_count` int
(
    11
) DEFAULT 0 COMMENT '当日更新',
    `delete_count` int
(
    11
) DEFAULT 0 COMMENT '当日删除',
    `get_count` int
(
    11
) DEFAULT 0 COMMENT '当日GET次数',
    `encrypt_count` int
(
    11
) DEFAULT 0 COMMENT '当日加密次数',
    `decrypt_count` int
(
    11
) DEFAULT 0 COMMENT '当日解密次数',
    `failed_count` int
(
    11
) DEFAULT 0 COMMENT '当日失败次数',
    `rotation_count` int
(
    11
) DEFAULT 0 COMMENT '当日轮换次数',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` datetime NOT NULL,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_stat_date`
(
    `stat_date`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每日统计表(FEATURE026,看板数据源)';


-- ============================================================================
-- 可选：种子数据（本脚本默认不灌，要不要灌由人决定）
--
-- init.sql 尾部有一条主密钥种子（把默认主密钥的 MD5 写进主密钥历史表）：
--
--   INSERT IGNORE INTO `z_mist_master_key_history`
--       (`key_alias`, `key_version`, `key_md5`, `algorithm`, `enabled`, `activated_time`, `creator`)
--   VALUES ('mist-default', 'v1', MD5('z-mist-default-master-key-2024'), 'AES', 1, NOW(), 'system');
--
-- ⚠️ 上面那行是 init.sql 里的原文（默认主密钥是编译期常量、公开在源码里，
--    只适合 dev 兜底）。生产要灌的话请用真实主密钥的指纹，且主密钥本身
--    走环境变量 Z_MIST_MASTER_KEY / JVM 参数 -Dz-mist.master-key 提供，
--    不要把明文写进本文件。
-- ============================================================================
