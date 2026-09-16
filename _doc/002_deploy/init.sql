-- z-mist 密钥管理平台数据库
CREATE
DATABASE IF NOT EXISTS z_mist CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE
z_mist;

-- 密钥主表
DROP TABLE IF EXISTS `z_mist_secret_info`;
CREATE TABLE IF NOT EXISTS `z_mist_secret_info`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `secret_key` varchar
(
    255
) NOT NULL COMMENT '密钥标识（唯一）',
    `secret_name` varchar
(
    128
) NOT NULL COMMENT '密钥名称',
    `group` varchar
(
    128
) DEFAULT NULL COMMENT '密钥分组',
    `app_name` varchar
(
    128
) DEFAULT NULL COMMENT '应用名',
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '命名空间',
    `encrypted_value` longtext NOT NULL COMMENT '加密后的密钥值',
    `value_md5` varchar
(
    32
) DEFAULT NULL COMMENT '密钥值MD5',
    `encrypt_algorithm` varchar
(
    32
) DEFAULT 'AES' COMMENT '加密算法',
    `key_version` varchar
(
    32
) DEFAULT 'v1' COMMENT '密钥版本',
    `secret_type` varchar
(
    32
) DEFAULT 'text' COMMENT '密钥类型（text/cert/password/key）',
    `description` varchar
(
    512
) DEFAULT NULL COMMENT '密钥描述',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL COMMENT '修改时间',
    `creator_staff_no` varchar
(
    255
) COMMENT '创建人工号',
    `creator_staff_nick_nm` varchar
(
    255
) COMMENT '创建人昵称',
    `source_ip` varchar
(
    50
) DEFAULT NULL COMMENT '创建IP',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_secret_key_group_namespace`
(
    `secret_key`,
    `group`,
    `namespace`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密钥信息表';

-- 密钥历史版本表
DROP TABLE IF EXISTS `z_mist_secret_history`;
CREATE TABLE IF NOT EXISTS `z_mist_secret_history`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `nid` bigint
(
    20
) NOT NULL COMMENT '密钥历史ID',
    `secret_key` varchar
(
    255
) NOT NULL COMMENT '密钥标识',
    `group` varchar
(
    128
) DEFAULT NULL,
    `app_name` varchar
(
    128
) DEFAULT NULL,
    `encrypted_value` longtext NOT NULL COMMENT '加密后的密钥值',
    `value_md5` varchar
(
    32
) DEFAULT NULL,
    `key_version` varchar
(
    32
) DEFAULT NULL COMMENT '密钥版本',
    `op_type` char
(
    10
) DEFAULT NULL COMMENT '操作类型（新增/修改/删除）',
    `operator_staff_no` varchar
(
    255
) COMMENT '操作人工号',
    `operator_ip` varchar
(
    50
) DEFAULT NULL COMMENT '操作IP',
    `gmt_create` datetime NOT NULL COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL COMMENT '修改时间',
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '命名空间',
    PRIMARY KEY
(
    `id`
),
    KEY `idx_gmt_modified`
(
    `gmt_modified`
),
    KEY `idx_secret_key`
(
    `secret_key`,
    `group`,
    `namespace`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密钥历史版本表';

-- 密钥访问授权表
DROP TABLE IF EXISTS `z_mist_secret_acl`;
CREATE TABLE IF NOT EXISTS `z_mist_secret_acl`
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
) DEFAULT '',
    `authorized_app` varchar
(
    128
) NOT NULL COMMENT '授权应用',
    `authorized_env` varchar
(
    32
) DEFAULT NULL COMMENT '授权环境（dev/test/prod）',
    `permission_level` varchar
(
    16
) DEFAULT 'read' COMMENT '权限级别（read/decrypt）',
    `expire_time` datetime DEFAULT NULL COMMENT '授权过期时间',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_secret_app_env`
(
    `secret_key`,
    `group`,
    `namespace`,
    `authorized_app`,
    `authorized_env`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密钥访问授权表';

-- 应用注册表
DROP TABLE IF EXISTS `z_mist_app_info`;
CREATE TABLE IF NOT EXISTS `z_mist_app_info`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_name` varchar
(
    128
) NOT NULL COMMENT '应用名',
    `app_secret` varchar
(
    255
) NOT NULL COMMENT '应用密钥',
    `app_type` varchar
(
    32
) DEFAULT 'server' COMMENT '应用类型（server/client）',
    `namespace` varchar
(
    128
) DEFAULT '' COMMENT '所属命名空间',
    `description` varchar
(
    512
) DEFAULT NULL,
    `enabled` tinyint
(
    1
) DEFAULT 1 COMMENT '是否启用',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL COMMENT '修改时间',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_app_name_namespace`
(
    `app_name`,
    `namespace`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用注册表';

-- 用户表
DROP TABLE IF EXISTS `z_mist_users`;
CREATE TABLE IF NOT EXISTS `z_mist_users`
(
    `username`
    varchar
(
    50
) NOT NULL COMMENT '用户名',
    `password` varchar
(
    500
) NOT NULL COMMENT '密码（BCrypt加密）',
    `enabled` tinyint
(
    1
) NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY
(
    `username`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
DROP TABLE IF EXISTS `z_mist_roles`;
CREATE TABLE IF NOT EXISTS `z_mist_roles`
(
    `username`
    varchar
(
    50
) NOT NULL COMMENT '关联用户名',
    `role` varchar
(
    50
) NOT NULL COMMENT '角色名',
    PRIMARY KEY
(
    `username`,
    `role`
),
    KEY `idx_username`
(
    `username`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色映射表';

-- 权限表
DROP TABLE IF EXISTS `z_mist_permissions`;
CREATE TABLE IF NOT EXISTS `z_mist_permissions`
(
    `role`
    varchar
(
    50
) NOT NULL COMMENT '角色名',
    `resource` varchar
(
    255
) NOT NULL COMMENT '资源标识',
    `action` varchar
(
    8
) NOT NULL COMMENT '权限操作（read/write/delete）',
    PRIMARY KEY
(
    `role`,
    `resource`,
    `action`
),
    KEY `idx_role`
(
    `role`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限映射表';

-- 初始化默认用户（用户名：admin，密码：admin，BCrypt加密后的值）
INSERT
IGNORE INTO `z_mist_users` (`username`, `password`, `enabled`)
VALUES ('admin', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', 1);

-- 初始化默认角色（admin用户关联管理员角色）
INSERT
IGNORE INTO `z_mist_roles` (`username`, `role`)
VALUES ('admin', 'ROLE_ADMIN');

-- ============================================================
-- FEATURE021: 密钥访问日志表
-- ============================================================
DROP TABLE IF EXISTS `z_mist_secret_access_log`;
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
DROP TABLE IF EXISTS `z_mist_secret_tag`;
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
DROP TABLE IF EXISTS `z_mist_secret_rotation_policy`;
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
DROP TABLE IF EXISTS `z_mist_secret_rotation_history`;
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
DROP TABLE IF EXISTS `z_mist_secret_dynamic`;
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
DROP TABLE IF EXISTS `z_mist_master_key_history`;
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
DROP TABLE IF EXISTS `z_mist_notification_log`;
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
DROP TABLE IF EXISTS `z_mist_stats_daily`;
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

-- 初始化默认主密钥记录(用于多版本管理)
INSERT
IGNORE INTO `z_mist_master_key_history`
    (`key_alias`, `key_version`, `key_md5`, `algorithm`, `enabled`, `activated_time`, `creator`)
VALUES ('mist-default', 'v1', MD5('z-mist-default-master-key-2024'), 'AES', 1, NOW(), 'system');
