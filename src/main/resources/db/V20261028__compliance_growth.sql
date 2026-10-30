-- ============================================================
-- 合规改造 + 积分来源 + 运营能力 + 安全加固（一次性迁移，幂等）
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p ttdemo < V20261028__compliance_growth.sql
-- ============================================================

-- ---------- 1. sys_user 扩展 ----------
SET @db := DATABASE();

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `agreement_version` VARCHAR(16) NULL COMMENT ''已同意的协议版本''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'agreement_version');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `agreed_at` DATETIME NULL COMMENT ''协议同意时间''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'agreed_at');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `invited_at` DATETIME NULL COMMENT ''被邀请注册时间''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'invited_at');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'login_fail_count');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `lock_until` DATETIME NULL COMMENT ''锁定到期时间''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'lock_until');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''令牌版本，自增即强制下线''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'token_version');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `deleted_flag` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 已注销''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'deleted_flag');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. 举报 ----------
CREATE TABLE IF NOT EXISTS `sys_report` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reporter_id`   BIGINT UNSIGNED NOT NULL,
  `target_type`   TINYINT      NOT NULL COMMENT '1预测 2创作者 3用户',
  `target_id`     BIGINT UNSIGNED NOT NULL,
  `reason_type`   TINYINT      NOT NULL COMMENT '1虚假宣传 2诱导赌博 3侵权 4辱骂 5其他',
  `content`       VARCHAR(500) NULL COMMENT '补充说明',
  `evidence_url`  VARCHAR(500) NULL,
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1待处理 2已处理 3已驳回',
  `handle_by`     BIGINT UNSIGNED NULL,
  `handle_time`   DATETIME     NULL,
  `handle_result` VARCHAR(500) NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_reporter` (`reporter_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容举报';

-- ---------- 3. 签到 ----------
CREATE TABLE IF NOT EXISTS `usr_sign_in` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `sign_date`     DATE         NOT NULL,
  `streak_days`   INT          NOT NULL DEFAULT 1,
  `points`        INT          NOT NULL DEFAULT 0 COMMENT '本次发放积分（含连签奖励）',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`),
  KEY `idx_user` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到';

-- ---------- 4. 注销申请 ----------
CREATE TABLE IF NOT EXISTS `usr_deactivation` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `phone`        VARCHAR(20)  NOT NULL,
  `reason`       VARCHAR(200) NULL,
  `points_left`  BIGINT       NOT NULL DEFAULT 0 COMMENT '注销时剩余积分',
  `refund_fen`   INT          NOT NULL DEFAULT 0 COMMENT '按充值比例折算的可退金额（分）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1待处理 2已注销 3已驳回',
  `handle_by`    BIGINT UNSIGNED NULL,
  `handle_time`  DATETIME     NULL,
  `handle_remark` VARCHAR(500) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号注销申请';

-- ---------- 5. 置顶推广 ----------
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `prd_prediction` ADD COLUMN `top_until` DATETIME NULL COMMENT ''置顶到期时间''',
    'SELECT 1') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'prd_prediction' AND COLUMN_NAME = 'top_until');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS `ord_promotion` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prediction_id` BIGINT UNSIGNED NOT NULL,
  `creator_id`    BIGINT UNSIGNED NOT NULL,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `points_cost`   BIGINT       NOT NULL,
  `days`          INT          NOT NULL,
  `start_at`      DATETIME     NOT NULL,
  `end_at`        DATETIME     NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prediction` (`prediction_id`, `end_at`),
  KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测置顶推广';

-- ---------- 6. 配置项 ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `value_type`, `remark`, `editable`) VALUES
('agreement.version',          '2026-10-28', 'STRING', '当前生效的协议版本，用户注册时记录', 1),
('agreement.required',         '1',          'BOOL',   '注册是否必须勾选同意协议', 1),
('point.sign.in.points',       '2',          'INT',    '每日签到基础积分', 1),
('point.sign.streak.bonus',    '10',         'INT',    '连续签到满 7 天额外奖励积分', 1),
('point.invite.reward',        '20',         'INT',    '邀请人注册奖励积分', 1),
('point.invite.recharge.reward','30',        'INT',    '被邀请人首充后邀请人追加奖励', 1),
('point.newbie.bonus',         '30',         'INT',    '新用户注册礼包积分', 1),
('point.newbie.expire.days',   '7',          'INT',    '新人礼包积分有效期（天）', 1),
('promote.points.per.day',     '50',         'INT',    '置顶推广每天消耗积分', 1),
('auth.login.fail.limit',      '5',          'INT',    '连续登录失败锁定阈值', 1),
('auth.login.lock.minutes',    '15',         'INT',    '登录失败锁定时长（分钟）', 1),
('report.auto.offline.threshold','3',        'INT',    '同一预测被举报达到次数后自动下架（0 关闭）', 1)
ON DUPLICATE KEY UPDATE `remark` = VALUES(`remark`);
