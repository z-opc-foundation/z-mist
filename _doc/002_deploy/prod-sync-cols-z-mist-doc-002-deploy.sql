-- =====================================================================
-- 结构对齐回写 (缺列) — 生成日 2026-10-24
-- 来源: 生产 oc 只读 mysqldump --no-data
-- 口径: 只加不改; 只收「两种独立解析实现都判缺」的列, 歧义区不动。
-- 用法: mysql <库名> < 本文件 (脚本用 DELIMITER, 走 mysql 客户端执行)。
-- 位置: 与本表 CREATE 语句同目录 (z-mist/_doc/002_deploy)
-- =====================================================================

-- 逐列补齐: 只在列不存在时才 ALTER ⇒ 可重复执行。
-- MySQL **不支持** ALTER TABLE ... ADD COLUMN IF NOT EXISTS (那是 MariaDB 语法),
-- 所以幂等靠 information_schema 判定 + 动态 SQL, 不靠语法糖。
DROP PROCEDURE IF EXISTS `zopc_sync_prod_cols_z-mist-doc-002-deploy`;
DELIMITER $$
CREATE PROCEDURE `zopc_sync_prod_cols_z-mist-doc-002-deploy`()
BEGIN
  DECLARE v_t VARCHAR(64);
  DECLARE v_c VARCHAR(64);
  DECLARE v_d TEXT;
  DECLARE cur CURSOR FOR SELECT tbl, col, defn FROM `_zopc_prod_sync_cols_z-mist-doc-002-deploy`;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_t = NULL;
  OPEN cur;
  lp: LOOP
    FETCH cur INTO v_t, v_c, v_d;
    IF v_t IS NULL THEN LEAVE lp; END IF;
    IF EXISTS (
         SELECT 1 FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_t)
       AND NOT EXISTS (
         SELECT 1 FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_t AND COLUMN_NAME = v_c) THEN
      SET @ddl = CONCAT('ALTER TABLE `', v_t, '` ADD COLUMN `', v_c, '` ', v_d);
      PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

DROP TEMPORARY TABLE IF EXISTS `_zopc_prod_sync_cols_z-mist-doc-002-deploy`;
CREATE TEMPORARY TABLE `_zopc_prod_sync_cols_z-mist-doc-002-deploy` (tbl VARCHAR(64), col VARCHAR(64), defn TEXT);
INSERT INTO `_zopc_prod_sync_cols_z-mist-doc-002-deploy` (tbl, col, defn) VALUES
  ('z_mist_app_info', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_app_info', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_permissions', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_permissions', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_roles', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_roles', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_acl', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_acl', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_history', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_history', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_info', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_secret_info', 'tenant_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_users', 'domain_code', 'varchar(64) DEFAULT NULL'),
  ('z_mist_users', 'tenant_code', 'varchar(64) DEFAULT NULL');

CALL `zopc_sync_prod_cols_z-mist-doc-002-deploy`();
DROP PROCEDURE IF EXISTS `zopc_sync_prod_cols_z-mist-doc-002-deploy`;
DROP TEMPORARY TABLE IF EXISTS `_zopc_prod_sync_cols_z-mist-doc-002-deploy`;
