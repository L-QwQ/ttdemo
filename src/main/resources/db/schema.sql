-- =====================================================================
-- ttdemo 全库建表脚本（MySQL 8.0+，utf8mb4）
--
-- 执行（唯一一条命令即可完成全库初始化）：
--   mysql --default-character-set=utf8mb4 -uroot -p < src/main/resources/db/schema.sql
--
-- 幂等性：全脚本可重复执行。建表用 CREATE TABLE IF NOT EXISTS、
--         加列先查 information_schema、种子数据用 ON DUPLICATE KEY UPDATE，
--         因此对已有库重跑不会报错，也不会覆盖已调整过的配置值。
--
-- 约定：积分 BIGINT 整数；金额 INT 分；时间 DATETIME；软删除字段 deleted
--
-- 变更记录（向后兼容，历史库直接重跑本脚本即可，幂等）：
--   · 复式号码本：usr_pick.red_numbers 128 → 255、blue_numbers 64 → 128。
--     复式存的是整个号码池（双色球 8+2 主号 23 字符、20 码补零后约 60 字符），原长度余量偏紧。
--   · 号码本兑奖：复式按 C(n,k) 拆注逐注判定后汇总（bets / winBets / breakdown 由接口计算），无表结构变化。
--   · 预测项可见性：付费未解锁项的号码只在服务层裁剪，表结构不变。
--   · 随机预测复式：由请求参数 betMode=SINGLE/MULTI 控制，结果不落库，无表结构变化。
-- =====================================================================


-- =====================================================================
-- 第一部分：账号体系与基础运营
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `ttdemo` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ttdemo`;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`         VARCHAR(20)     NOT NULL COMMENT '手机号（登录账号）',
    `password`      VARCHAR(100)             DEFAULT NULL COMMENT 'BCrypt 密码，验证码注册用户可为空',
    `nickname`      VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '头像地址',
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    `last_login_at` DATETIME                 DEFAULT NULL COMMENT '最后登录时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_role_status` (`role`, `status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- 短信验证码表（不依赖 Redis，开箱即用；量大后可平滑迁移到 Redis）
CREATE TABLE IF NOT EXISTS `sms_code`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`      VARCHAR(20)     NOT NULL COMMENT '手机号',
    `code`       VARCHAR(10)     NOT NULL COMMENT '验证码',
    `scene`      VARCHAR(30)     NOT NULL COMMENT '场景：REGISTER / LOGIN / RESET_PASSWORD',
    `expired_at` DATETIME        NOT NULL COMMENT '过期时间',
    `used`       TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已使用：0 否 / 1 是',
    `attempts`   INT             NOT NULL DEFAULT 0 COMMENT '校验失败次数',
    `request_ip` VARCHAR(64)              DEFAULT NULL COMMENT '请求 IP',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone_scene_created` (`phone`, `scene`, `created_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短信验证码表';

-- 通知公告表
CREATE TABLE IF NOT EXISTS `sys_notice`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`          VARCHAR(100)    NOT NULL COMMENT '标题',
    `content`        TEXT            NOT NULL COMMENT '正文',
    `type`           VARCHAR(20)     NOT NULL DEFAULT 'ANNOUNCEMENT' COMMENT '类型：ANNOUNCEMENT 公告 / NOTICE 通知',
    `level`          VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '级别：NORMAL 普通 / IMPORTANT 重要 / URGENT 紧急',
    `target_type`    VARCHAR(20)     NOT NULL DEFAULT 'ALL' COMMENT '接收范围：ALL 全部用户 / SPECIFIC 指定用户',
    `status`         TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 / 1 已发布',
    `publisher_id`   BIGINT UNSIGNED          DEFAULT NULL COMMENT '发布人 id',
    `publisher_name` VARCHAR(50)              DEFAULT NULL COMMENT '发布人昵称快照',
    `published_at`   DATETIME                 DEFAULT NULL COMMENT '发布时间',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status_published` (`status`, `published_at`),
    KEY `idx_type` (`type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知公告表';

-- 通知接收人表（发布时按目标人群展开写入，已读状态记录在此）
CREATE TABLE IF NOT EXISTS `sys_notice_receiver`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `notice_id`  BIGINT UNSIGNED NOT NULL COMMENT '通知 id',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '接收人 id',
    `read_at`    DATETIME                 DEFAULT NULL COMMENT '阅读时间，为空表示未读',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`),
    KEY `idx_user_read` (`user_id`, `read_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知接收人表';

-- 用户反馈表
CREATE TABLE IF NOT EXISTS `sys_feedback`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '提交人 id',
    `user_phone`  VARCHAR(20)     NOT NULL COMMENT '提交人手机号快照',
    `type`        VARCHAR(20)     NOT NULL DEFAULT 'OTHER' COMMENT '类型：BUG 故障 / FEATURE 功能建议 / EXPERIENCE 体验问题 / OTHER 其他',
    `title`       VARCHAR(100)    NOT NULL COMMENT '标题',
    `content`     TEXT            NOT NULL COMMENT '反馈内容',
    `contact`     VARCHAR(100)             DEFAULT NULL COMMENT '联系方式（手机号/微信/邮箱）',
    `status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 / 1 处理中 / 2 已解决 / 3 已关闭',
    `reply`       TEXT                     DEFAULT NULL COMMENT '管理员回复',
    `replied_by`  BIGINT UNSIGNED          DEFAULT NULL COMMENT '回复人 id',
    `replied_at`  DATETIME                 DEFAULT NULL COMMENT '回复时间',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status` (`status`),
    KEY `idx_type` (`type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户反馈表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS `sys_login_log`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED          DEFAULT NULL COMMENT '用户 id，注册失败时为空',
    `phone`      VARCHAR(20)     NOT NULL COMMENT '手机号',
    `type`       VARCHAR(30)     NOT NULL COMMENT '类型：REGISTER / LOGIN_SMS / LOGIN_PASSWORD / ADMIN_LOGIN',
    `success`    TINYINT         NOT NULL DEFAULT 1 COMMENT '是否成功：1 是 / 0 否',
    `message`    VARCHAR(200)             DEFAULT NULL COMMENT '结果说明',
    `ip`         VARCHAR(64)              DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` VARCHAR(255)             DEFAULT NULL COMMENT 'User-Agent',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='登录日志表';


-- =====================================================================
-- 第二部分：彩票预测平台（彩种/期次/预测/交易/积分/结算/评分/消息）
-- =====================================================================

-- 彩票预测平台 v1.1 增量脚本
-- 约定：积分 BIGINT 整数；金额 INT 分；时间 DATETIME；软删除 deleted

-- ---------- 0. 幂等辅助：列不存在才添加 ----------
DROP PROCEDURE IF EXISTS `add_col_if_absent`;
DELIMITER $$
CREATE PROCEDURE `add_col_if_absent`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_col_if_absent('sys_user', 'gender',          '`gender` TINYINT NOT NULL DEFAULT 0 COMMENT ''0未知 1男 2女''');
CALL add_col_if_absent('sys_user', 'birthday',        '`birthday` DATE NULL COMMENT ''生日''');
CALL add_col_if_absent('sys_user', 'realname_status', '`realname_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0未实名 1已实名 2审核中 3驳回''');
CALL add_col_if_absent('sys_user', 'creator_status',  '`creator_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0普通 1申请中 2已认证 3驳回 4封禁''');
CALL add_col_if_absent('sys_user', 'invite_code',     '`invite_code` VARCHAR(12) NULL COMMENT ''邀请码''');
CALL add_col_if_absent('sys_user', 'inviter_id',      '`inviter_id` BIGINT UNSIGNED NULL COMMENT ''邀请人''');
CALL add_col_if_absent('sys_user', 'device_id',       '`device_id` VARCHAR(64) NULL COMMENT ''设备指纹''');
CALL add_col_if_absent('sys_user', 'credit_score',    '`credit_score` INT NOT NULL DEFAULT 100 COMMENT ''信誉分''');
CALL add_col_if_absent('sys_user', 'risk_status',     '`risk_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0正常 1限制消费 2限制发布 3冻结''');

DROP PROCEDURE IF EXISTS `add_idx_if_absent`;
DELIMITER $$
CREATE PROCEDURE `add_idx_if_absent`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD KEY `', p_idx, '` (', p_cols, ')');
        PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_idx_if_absent('sys_user', 'idx_creator_status', '`creator_status`');
CALL add_idx_if_absent('sys_user', 'idx_invite_code',    '`invite_code`');

-- ---------- 1. 实名认证 ----------
CREATE TABLE IF NOT EXISTS `usr_realname` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `real_name`    VARBINARY(256) NOT NULL COMMENT 'AES-256 密文',
  `id_card_no`   VARBINARY(256) NOT NULL COMMENT 'AES-256 密文',
  `id_card_hash` CHAR(64)       NOT NULL COMMENT 'SHA256 查重',
  `mobile_enc`   VARBINARY(128) NOT NULL COMMENT 'AES-256 密文',
  `age`          INT            NULL,
  `audit_status` TINYINT        NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  `audit_remark` VARCHAR(255)   NULL,
  `audit_by`     BIGINT UNSIGNED NULL,
  `audit_time`   DATETIME       NULL,
  `created_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  UNIQUE KEY `uk_id_card` (`id_card_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实名认证';

-- ---------- 2. 创作者 ----------
CREATE TABLE IF NOT EXISTS `cre_creator` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `pen_name`          VARCHAR(32)  NOT NULL,
  `intro`             VARCHAR(500) NULL,
  `tags`              VARCHAR(255) NULL,
  `apply_reason`      VARCHAR(500) NULL,
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1待审 2通过 3驳回 4封禁',
  `audit_remark`      VARCHAR(255) NULL,
  `audit_by`          BIGINT UNSIGNED NULL,
  `audit_time`        DATETIME     NULL,
  `creator_level`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1普通 2优质 3金牌',
  `platform_fee_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.2000,
  `newbie_until`      DATETIME     NULL COMMENT '新认证优惠截止（首90天）',
  `certified_at`      DATETIME     NULL,
  `fav_count`         INT          NOT NULL DEFAULT 0,
  `total_income`      BIGINT       NOT NULL DEFAULT 0,
  `total_predictions` INT          NOT NULL DEFAULT 0,
  `withdraw_rate`     DECIMAL(6,4) NOT NULL DEFAULT 0,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  UNIQUE KEY `uk_pen_name` (`pen_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者档案';

CREATE TABLE IF NOT EXISTS `cre_follow` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `creator_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_creator` (`user_id`, `creator_id`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者关注';

-- ---------- 3. 彩种与期次 ----------
CREATE TABLE IF NOT EXISTS `lot_lottery_type` (
  `id`                  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`                VARCHAR(16)  NOT NULL,
  `name`                VARCHAR(32)  NOT NULL,
  `category`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1福彩 2体彩',
  `draw_weekdays`       VARCHAR(16)  NULL,
  `draw_time_of_day`    TIME         NULL,
  `sale_end_offset_min` INT          NOT NULL DEFAULT 20,
  `play_config`         JSON         NULL,
  `icon`                VARCHAR(255) NULL,
  `sort_no`             INT          NOT NULL DEFAULT 0,
  `status`              TINYINT      NOT NULL DEFAULT 1,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='彩种字典';

CREATE TABLE IF NOT EXISTS `lot_issue` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `type_code`     VARCHAR(16)  NOT NULL,
  `issue_no`      VARCHAR(16)  NOT NULL,
  `draw_time`     DATETIME     NOT NULL,
  `sale_end_time` DATETIME     NOT NULL,
  `red_numbers`   VARCHAR(128) NULL,
  `blue_numbers`  VARCHAR(64)  NULL,
  `extra`         JSON         NULL,
  `draw_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未开奖 1已开奖 2延期 3取消',
  `settle_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0待结算 1结算中 2已结算 3失败',
  `settle_time`   DATETIME     NULL,
  `settle_error`  VARCHAR(500) NULL,
  `data_source`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1自动 2人工',
  `review_ticket` VARCHAR(40)  NULL COMMENT '人工录入复核票据',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_issue` (`type_code`, `issue_no`),
  KEY `idx_draw_settle` (`draw_status`, `settle_status`),
  KEY `idx_sale_end` (`sale_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期次';

CREATE TABLE IF NOT EXISTS `lot_sync_error` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `type_code`  VARCHAR(16)  NOT NULL,
  `issue_no`   VARCHAR(16)  NULL,
  `error_type` VARCHAR(32)  NOT NULL,
  `raw_data`   TEXT         NULL,
  `message`    VARCHAR(500) NULL,
  `handled`    TINYINT      NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_handled` (`handled`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开奖同步异常';

-- ---------- 4. 预测 ----------
CREATE TABLE IF NOT EXISTS `prd_prediction` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prediction_no`     VARCHAR(32)  NOT NULL,
  `creator_id`        BIGINT UNSIGNED NOT NULL,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `type_code`         VARCHAR(16)  NOT NULL,
  `issue_id`          BIGINT UNSIGNED NOT NULL,
  `issue_no`          VARCHAR(16)  NOT NULL,
  `title`             VARCHAR(60)  NOT NULL,
  `play_type`         VARCHAR(16)  NOT NULL,
  `summary`           VARCHAR(200) NOT NULL,
  `content_html`      MEDIUMTEXT   NULL,
  `content_images`    JSON         NULL,
  `price_base`        INT          NOT NULL,
  `price_coefficient` DECIMAL(6,4) NOT NULL DEFAULT 1.0000,
  `price_points`      INT          NOT NULL DEFAULT 0,
  `is_free`           TINYINT      NOT NULL DEFAULT 0,
  `status`            TINYINT      NOT NULL DEFAULT 0,
  `online_time`       DATETIME     NULL,
  `lock_time`         DATETIME     NULL,
  `settle_time`       DATETIME     NULL,
  `content_hash`      CHAR(64)     NULL,
  `audit_by`          BIGINT UNSIGNED NULL,
  `audit_time`        DATETIME     NULL,
  `audit_remark`      VARCHAR(255) NULL,
  `risk_hit`          VARCHAR(500) NULL,
  `item_count`        INT          NOT NULL DEFAULT 0,
  `hit_count`         INT          NOT NULL DEFAULT 0,
  `hit_rate`          DECIMAL(6,4) NULL,
  `is_hit`            TINYINT      NULL,
  `view_count`        INT          NOT NULL DEFAULT 0,
  `buyer_count`       INT          NOT NULL DEFAULT 0,
  `total_income`      BIGINT       NOT NULL DEFAULT 0,
  `like_count`        INT          NOT NULL DEFAULT 0,
  `version`           INT          NOT NULL DEFAULT 0,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`prediction_no`),
  KEY `idx_issue_status` (`type_code`, `issue_id`, `status`),
  KEY `idx_creator` (`creator_id`, `created_at`),
  KEY `idx_status_lock` (`status`, `lock_time`),
  KEY `idx_online` (`online_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测方案';

CREATE TABLE IF NOT EXISTS `prd_prediction_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prediction_id` BIGINT UNSIGNED NOT NULL,
  `item_type`     VARCHAR(24)  NOT NULL,
  `item_value`    VARCHAR(255) NOT NULL COMMENT '预测项取值；号码型为逗号分隔的号码池（复式可 20+ 个，如 8 码红球池）',
  `item_label`    VARCHAR(128) NULL,
  `is_free`       TINYINT      NOT NULL DEFAULT 0,
  `weight`        DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  `sort_no`       INT          NOT NULL DEFAULT 0,
  `hit_result`    TINYINT      NULL,
  `hit_detail`    JSON         NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pred` (`prediction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测项';

CREATE TABLE IF NOT EXISTS `prd_audit_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `biz_type`     VARCHAR(24)  NOT NULL,
  `biz_id`       BIGINT UNSIGNED NOT NULL,
  `audit_result` TINYINT      NOT NULL,
  `audit_remark` VARCHAR(255) NULL,
  `risk_level`   TINYINT      NOT NULL DEFAULT 0,
  `risk_detail`  VARCHAR(500) NULL,
  `audit_by`     BIGINT UNSIGNED NOT NULL,
  `audit_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cost_ms`      INT          NULL,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_time` (`audit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录';

CREATE TABLE IF NOT EXISTS `prd_view_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NULL,
  `prediction_id` BIGINT UNSIGNED NOT NULL,
  `device_id`     VARCHAR(64)  NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_pred` (`prediction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测浏览日志';

-- ---------- 5. 交易 ----------
CREATE TABLE IF NOT EXISTS `ord_prediction_order` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`       VARCHAR(32)  NOT NULL,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `prediction_id`  BIGINT UNSIGNED NOT NULL,
  `creator_id`     BIGINT UNSIGNED NOT NULL,
  `type_code`      VARCHAR(16)  NOT NULL,
  `issue_id`       BIGINT UNSIGNED NOT NULL,
  `price_points`   INT          NOT NULL,
  `platform_fee`   INT          NOT NULL DEFAULT 0,
  `creator_income` INT          NOT NULL DEFAULT 0,
  `status`         TINYINT      NOT NULL DEFAULT 1,
  `pay_time`       DATETIME     NULL,
  `refund_time`    DATETIME     NULL,
  `settle_id`      BIGINT UNSIGNED NULL,
  `client_ip`      VARCHAR(64)  NULL,
  `device_id`      VARCHAR(64)  NULL,
  `risk_flag`      TINYINT      NOT NULL DEFAULT 0,
  `idempotent_key` VARCHAR(64)  NOT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`order_no`),
  UNIQUE KEY `uk_user_pred` (`user_id`, `prediction_id`),
  UNIQUE KEY `uk_idem` (`idempotent_key`),
  KEY `idx_user` (`user_id`, `status`),
  KEY `idx_creator_settle` (`creator_id`, `issue_id`, `status`),
  KEY `idx_pred` (`prediction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测解锁订单';

CREATE TABLE IF NOT EXISTS `ord_recharge_order` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(32)  NOT NULL,
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `amount_fen`       INT          NOT NULL,
  `points`           BIGINT       NOT NULL,
  `bonus_points`     BIGINT       NOT NULL DEFAULT 0,
  `pay_channel`      TINYINT      NOT NULL COMMENT '1微信 2支付宝 9Mock',
  `channel_trade_no` VARCHAR(64)  NULL,
  `status`           TINYINT      NOT NULL DEFAULT 1,
  `pay_time`         DATETIME     NULL,
  `notify_time`      DATETIME     NULL,
  `client_ip`        VARCHAR(64)  NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`order_no`),
  UNIQUE KEY `uk_channel_trade` (`pay_channel`, `channel_trade_no`),
  KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分充值订单';

CREATE TABLE IF NOT EXISTS `ord_pay_notify_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel`       TINYINT      NOT NULL,
  `order_no`      VARCHAR(32)  NULL,
  `raw_body`      TEXT         NULL,
  `sign_valid`    TINYINT      NOT NULL DEFAULT 0,
  `handle_result` VARCHAR(32)  NOT NULL,
  `error_msg`     VARCHAR(500) NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志';

-- ---------- 6. 积分与结算 ----------
CREATE TABLE IF NOT EXISTS `acc_point_account` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `available`      BIGINT NOT NULL DEFAULT 0,
  `frozen`         BIGINT NOT NULL DEFAULT 0,
  `total_recharge` BIGINT NOT NULL DEFAULT 0,
  `total_income`   BIGINT NOT NULL DEFAULT 0,
  `total_consume`  BIGINT NOT NULL DEFAULT 0,
  `total_expired`  BIGINT NOT NULL DEFAULT 0,
  `status`         TINYINT NOT NULL DEFAULT 1,
  `version`        INT NOT NULL DEFAULT 0,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户';

CREATE TABLE IF NOT EXISTS `acc_point_transaction` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `txn_no`         VARCHAR(40)  NOT NULL,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `account_type`   TINYINT      NOT NULL DEFAULT 1,
  `direction`      TINYINT      NOT NULL COMMENT '1收入 2支出',
  `amount`         BIGINT       NOT NULL COMMENT '绝对值 > 0',
  `balance_before` BIGINT       NOT NULL,
  `balance_after`  BIGINT       NOT NULL,
  `biz_type`       VARCHAR(32)  NOT NULL,
  `biz_no`         VARCHAR(64)  NOT NULL,
  `remark`         VARCHAR(255) NULL,
  `expire_at`      DATETIME     NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_txn_no` (`txn_no`),
  UNIQUE KEY `uk_biz` (`biz_type`, `biz_no`, `user_id`, `account_type`, `direction`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_expire` (`expire_at`, `direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水（禁止 UPDATE/DELETE）';

CREATE TABLE IF NOT EXISTS `acc_point_batch` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `txn_id`     BIGINT UNSIGNED NOT NULL,
  `biz_type`   VARCHAR(32)  NOT NULL,
  `amount`     BIGINT       NOT NULL,
  `remain`     BIGINT       NOT NULL,
  `expire_at`  DATETIME     NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_expire` (`user_id`, `expire_at`),
  KEY `idx_expire` (`expire_at`, `remain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分批次(FIFO)';

CREATE TABLE IF NOT EXISTS `acc_settlement` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `settle_no`      VARCHAR(32)  NOT NULL,
  `creator_id`     BIGINT UNSIGNED NOT NULL,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `type_code`      VARCHAR(16)  NOT NULL,
  `issue_id`       BIGINT UNSIGNED NOT NULL,
  `issue_no`       VARCHAR(16)  NOT NULL,
  `order_count`    INT          NOT NULL DEFAULT 0,
  `gross_points`   BIGINT       NOT NULL DEFAULT 0,
  `platform_fee`   BIGINT       NOT NULL DEFAULT 0,
  `creator_points` BIGINT       NOT NULL DEFAULT 0,
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1待入账 2已入账 3已撤销',
  `credit_time`    DATETIME     NULL,
  `cancel_reason`  VARCHAR(255) NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`settle_no`),
  UNIQUE KEY `uk_creator_issue` (`creator_id`, `issue_id`),
  KEY `idx_creator` (`creator_id`, `created_at`),
  KEY `idx_issue` (`issue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者结算单';

-- ---------- 7. 评分与榜单 ----------
CREATE TABLE IF NOT EXISTS `score_creator_accuracy` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `creator_id`         BIGINT UNSIGNED NOT NULL,
  `type_code`          VARCHAR(16)  NOT NULL DEFAULT 'ALL',
  `sample_size`        INT          NOT NULL DEFAULT 0,
  `hit_times`          INT          NOT NULL DEFAULT 0,
  `raw_hit_rate`       DECIMAL(6,4) NOT NULL DEFAULT 0,
  `weighted_hit_rate`  DECIMAL(6,4) NOT NULL DEFAULT 0,
  `wilson_lower`       DECIMAL(6,4) NOT NULL DEFAULT 0,
  `accuracy_score`     DECIMAL(6,2) NOT NULL DEFAULT 30.00,
  `current_hit_streak` INT          NOT NULL DEFAULT 0,
  `max_hit_streak`     INT          NOT NULL DEFAULT 0,
  `recent10_rate`      DECIMAL(6,4) NOT NULL DEFAULT 0,
  `total_view`         BIGINT       NOT NULL DEFAULT 0,
  `total_buyer`        BIGINT       NOT NULL DEFAULT 0,
  `total_income`       BIGINT       NOT NULL DEFAULT 0,
  `price_coefficient`  DECIMAL(6,4) NOT NULL DEFAULT 0.6000,
  `calc_version`       VARCHAR(16)  NOT NULL DEFAULT 'v1',
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator_type` (`creator_id`, `type_code`),
  KEY `idx_score` (`type_code`, `accuracy_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者准确度评分';

CREATE TABLE IF NOT EXISTS `score_daily_snapshot` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `creator_id`     BIGINT UNSIGNED NOT NULL,
  `type_code`      VARCHAR(16)  NOT NULL,
  `stat_date`      DATE         NOT NULL,
  `accuracy_score` DECIMAL(6,2) NOT NULL,
  `sample_size`    INT          NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator_type_date` (`creator_id`, `type_code`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分日快照';

CREATE TABLE IF NOT EXISTS `score_rank_snapshot` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rank_type`     VARCHAR(24)  NOT NULL,
  `type_code`     VARCHAR(16)  NOT NULL DEFAULT 'ALL',
  `period`        VARCHAR(16)  NOT NULL DEFAULT 'ALL',
  `snapshot_date` DATE         NOT NULL,
  `rank_no`       INT          NOT NULL,
  `creator_id`    BIGINT UNSIGNED NOT NULL,
  `metric_value`  DECIMAL(14,4) NOT NULL,
  `ext`           JSON         NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rank` (`rank_type`, `type_code`, `period`, `snapshot_date`, `rank_no`),
  KEY `idx_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='榜单快照';

-- ---------- 8. 消息与系统 ----------
CREATE TABLE IF NOT EXISTS `not_message` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `msg_type`   VARCHAR(32)  NOT NULL,
  `title`      VARCHAR(100) NOT NULL,
  `content`    VARCHAR(1000) NOT NULL,
  `biz_type`   VARCHAR(32)  NULL,
  `biz_id`     BIGINT UNSIGNED NULL,
  `is_read`    TINYINT      NOT NULL DEFAULT 0,
  `read_time`  DATETIME     NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';

CREATE TABLE IF NOT EXISTS `not_send_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel`     TINYINT      NOT NULL,
  `mobile_enc`  VARBINARY(128) NULL,
  `template_id` VARCHAR(64)  NULL,
  `content`     VARCHAR(500) NULL,
  `biz_type`    VARCHAR(32)  NULL,
  `biz_id`      BIGINT UNSIGNED NULL,
  `status`      TINYINT      NOT NULL DEFAULT 0,
  `error_msg`   VARCHAR(255) NULL,
  `retry_count` INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送日志';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64)  NOT NULL,
  `config_value` VARCHAR(1000) NOT NULL,
  `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING',
  `remark`       VARCHAR(255) NULL,
  `editable`     TINYINT      NOT NULL DEFAULT 1,
  `updated_by`   BIGINT UNSIGNED NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operator_id`   BIGINT UNSIGNED NOT NULL,
  `operator_name` VARCHAR(64)  NULL,
  `module`        VARCHAR(32)  NOT NULL,
  `action`        VARCHAR(64)  NOT NULL,
  `biz_type`      VARCHAR(32)  NULL,
  `biz_id`        BIGINT UNSIGNED NULL,
  `before_value`  TEXT         NULL,
  `after_value`   TEXT         NULL,
  `remark`        VARCHAR(500) NULL,
  `client_ip`     VARCHAR(64)  NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`, `created_at`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

CREATE TABLE IF NOT EXISTS `risk_blacklist` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `risk_type`  VARCHAR(16)  NOT NULL COMMENT 'PHONE/IP/DEVICE/ID_CARD',
  `risk_value` VARCHAR(128) NOT NULL,
  `reason`     VARCHAR(255) NULL,
  `operator_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_value` (`risk_type`, `risk_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控黑名单';

CREATE TABLE IF NOT EXISTS `risk_sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64)  NOT NULL,
  `category`   VARCHAR(32)  NOT NULL DEFAULT 'GENERAL' COMMENT 'BETTING/CLAIM/AD/CONTACT/GENERAL',
  `level`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1提示 2拦截',
  `status`     TINYINT      NOT NULL DEFAULT 1,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

-- ---------- 9. 种子数据：系统配置 ----------
INSERT INTO `sys_config` (`config_key`,`config_value`,`value_type`,`remark`) VALUES
('price.base.SINGLE',   '20',    'INT',     '单式推荐基础价（积分）'),
('price.base.MULTI',    '15',    'INT',     '复式推荐基础价'),
('price.base.DAN',      '10',    'INT',     '胆码推荐基础价'),
('price.base.KILL',     '10',    'INT',     '杀号推荐基础价'),
('price.base.FORM',     '6',     'INT',     '形态预测基础价'),
('price.base.AI_PACK',  '30',    'INT',     '综合方案包基础价'),
('price.max.points',    '200',   'INT',     '单份预测售价硬上限'),
('price.coef.slope',    '0.008', 'DECIMAL', '定价系数斜率'),
('price.coef.intercept','0.6',   'DECIMAL', '定价系数截距'),
('price.sample.full',   '30',    'INT',     '样本置信满值期数'),
('hit.threshold',       '0.6',   'DECIMAL', '综合命中阈值'),
('score.cold.start',    '30',    'DECIMAL', '冷启动中性分'),
('score.cold.cap',      '60',    'DECIMAL', '样本5-9期评分上限'),
('score.window.days',   '90',    'INT',     '评分统计时间窗口(天)'),
('score.window.max',    '100',   'INT',     '评分统计最多期数'),
('point.recharge.rate', '10',    'INT',     '1元兑换积分数量'),
('point.expire.months', '24',    'INT',     '积分有效期(月)'),
('point.recharge.min.fen','600','INT',     '单笔最低充值（分）'),
('point.recharge.max.fen','200000','INT',  '单笔最高充值（分）'),
('point.recharge.daily.fen','500000','INT','每日充值上限（分）'),
('platform.fee.rate',   '0.20',  'DECIMAL', '默认平台抽成'),
('platform.fee.newbie', '0.10',  'DECIMAL', '新认证创作者首90天抽成'),
('risk.max.prediction.per.issue', '5', 'INT', '同期次单创作者最多有效预测数'),
('feature.unlock.enabled',  'true', 'BOOLEAN', '解锁功能开关'),
('feature.recharge.enabled','true', 'BOOLEAN', '充值功能开关'),
('feature.settle.enabled',  'true', 'BOOLEAN', '结算任务开关'),
('refund.on.account.cancel','true','BOOLEAN', '注销可退未消费积分'),
('invite.register.points',  '20',  'INT',     '邀请注册奖励'),
('invite.first.recharge.points','30','INT',   '被邀请人首充后奖励'),
('sign.in.points',      '2',     'INT',     '每日签到积分'),
('sign.in.week.bonus',  '10',    'INT',     '连续7天签到额外奖励'),
('realname.min.age',    '18',    'INT',     '实名认证最小年龄（周岁）'),
('feature.draw.sync',   'true',  'BOOLEAN', '开奖数据自动同步开关')
ON DUPLICATE KEY UPDATE `remark` = VALUES(`remark`);

-- ---------- 10. 种子数据：彩种 ----------
INSERT INTO `lot_lottery_type`
(`code`,`name`,`category`,`draw_weekdays`,`draw_time_of_day`,`sale_end_offset_min`,`play_config`,`sort_no`,`status`) VALUES
('SSQ','双色球',1,'2,4,7','21:15:00',20,
 '{"groups":[{"key":"RED","name":"红球","min":1,"max":33,"pickMin":1,"pickMax":20,"drawCount":6,"supports":["SINGLE","MULTI","DAN","KILL"]},{"key":"BLUE","name":"蓝球","min":1,"max":16,"pickMin":1,"pickMax":1,"drawCount":1,"supports":["SINGLE","KILL"]}],"formItems":[{"key":"SUM_RANGE","name":"和值区间","min":21,"max":183},{"key":"SPAN","name":"跨度","min":0,"max":32},{"key":"ODD_EVEN","name":"奇偶比","pattern":"^\\\\d+:\\\\d+$"},{"key":"BIG_SMALL","name":"大小比","pattern":"^\\\\d+:\\\\d+$"}]}',1,1),
('DLT','大乐透',2,'1,3,6','21:25:00',20,
 '{"groups":[{"key":"RED","name":"前区","min":1,"max":35,"pickMin":1,"pickMax":18,"drawCount":5,"supports":["SINGLE","MULTI","DAN","KILL"]},{"key":"BLUE","name":"后区","min":1,"max":12,"pickMin":1,"pickMax":2,"drawCount":2,"supports":["SINGLE","KILL"]}],"formItems":[{"key":"SUM_RANGE","name":"和值区间","min":15,"max":165},{"key":"SPAN","name":"跨度","min":0,"max":34},{"key":"ODD_EVEN","name":"奇偶比","pattern":"^\\\\d+:\\\\d+$"}]}',2,1),
('FC3D','福彩3D',1,'0','21:15:00',20,
 '{"groups":[{"key":"RED","name":"号码","min":0,"max":9,"pickMin":3,"pickMax":3,"drawCount":3,"supports":["SINGLE","DAN","KILL"]}],"formItems":[{"key":"SUM_RANGE","name":"和值区间","min":0,"max":27},{"key":"SPAN","name":"跨度","min":0,"max":9},{"key":"ODD_EVEN","name":"奇偶比","pattern":"^\\\\d+:\\\\d+$"},{"key":"BIG_SMALL","name":"大小比","pattern":"^\\\\d+:\\\\d+$"}]}',3,1),
('PL3','排列3',2,'0','21:25:00',20,
 '{"groups":[{"key":"RED","name":"号码","min":0,"max":9,"pickMin":3,"pickMax":3,"drawCount":3,"supports":["SINGLE","DAN","KILL"]}],"formItems":[{"key":"SUM_RANGE","name":"和值区间","min":0,"max":27},{"key":"SPAN","name":"跨度","min":0,"max":9},{"key":"ODD_EVEN","name":"奇偶比","pattern":"^\\\\d+:\\\\d+$"}]}',4,1),
('KL8','快乐8',1,'0','21:30:00',20,
 '{"groups":[{"key":"RED","name":"号码","min":1,"max":80,"pickMin":1,"pickMax":10,"drawCount":20,"supports":["SINGLE","MULTI","DAN","KILL"]}],"formItems":[{"key":"SUM_RANGE","name":"和值区间","min":55,"max":755},{"key":"SPAN","name":"跨度","min":0,"max":79}]}',5,1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `play_config`=VALUES(`play_config`);

-- ---------- 11. 种子数据：敏感词（核心违规话术） ----------
INSERT INTO `risk_sensitive_word` (`word`,`category`,`level`) VALUES
('必中','CLAIM',2),('包中','CLAIM',2),('稳赚','CLAIM',2),('保底','CLAIM',2),
('百分百','CLAIM',2),('内部消息','CLAIM',2),('内部渠道','CLAIM',2),('泄露','CLAIM',2),
('跟单返利','BETTING',2),('倍投','BETTING',2),('资金跟投','BETTING',2),('包赔','BETTING',2),
('加微信','CONTACT',2),('加QQ','CONTACT',2),('私聊','CONTACT',1),('扫码','CONTACT',1),
('代购','BETTING',2),('代兑奖','BETTING',2),('合买','BETTING',1),('投注站','BETTING',1),
('收益率','GENERAL',1),('回报率','GENERAL',1),('盈亏','GENERAL',1)
ON DUPLICATE KEY UPDATE `level`=VALUES(`level`);

DROP PROCEDURE IF EXISTS `add_col_if_absent`;
DROP PROCEDURE IF EXISTS `add_idx_if_absent`;


-- =====================================================================
-- 第三部分：合规改造与运营能力（举报/签到/注销/推广）
-- =====================================================================

-- 合规改造 + 积分来源 + 运营能力 + 安全加固（一次性迁移，幂等）

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

-- 用户自选号码本（会员功能）：保存自己看好的号码，可按最新开奖自动兑奖
CREATE TABLE IF NOT EXISTS `usr_pick` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `type_code`     VARCHAR(16)  NOT NULL,
  `issue_no`      VARCHAR(16)  NULL COMMENT '绑定期号，按期兑奖；为空时按最新开奖兜底',
  `red_numbers`   VARCHAR(255) NOT NULL COMMENT '主号码，逗号分隔；复式存整个号码池（双色球可到 20 个），3D/排列3 为 3 位',
  `blue_numbers`  VARCHAR(128) NULL COMMENT '副号码：双色球蓝球 / 大乐透后区；复式存整个号码池',
  `remark`        VARCHAR(64)  NULL COMMENT '备注，便于区分自选/机选等',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`, `type_code`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自选号码本（会员功能；支持单式与复式，复式兑奖时按组合拆注）';

-- 历史库补 issue_no 列（新建库已在 CREATE 里包含）
SET @has_pick_issue = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usr_pick' AND COLUMN_NAME = 'issue_no');
SET @pick_ddl = IF(@has_pick_issue = 0,
    'ALTER TABLE `usr_pick` ADD COLUMN `issue_no` VARCHAR(16) NULL COMMENT ''绑定期号，按期兑奖；为空时按最新开奖兜底'' AFTER `type_code`',
    'DO 0');
PREPARE pick_stmt FROM @pick_ddl; EXECUTE pick_stmt; DEALLOCATE PREPARE pick_stmt;

-- 历史库放宽号码列（复式号码池比单式长得多：双色球 8+2 主号 23 字符、20 码约 60 字符，原 128/64 余量偏紧）
SET @pick_red_len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usr_pick' AND COLUMN_NAME = 'red_numbers');
SET @pick_red_ddl = IF(@pick_red_len IS NOT NULL AND @pick_red_len < 255,
    'ALTER TABLE `usr_pick` MODIFY COLUMN `red_numbers` VARCHAR(255) NOT NULL COMMENT ''主号码，逗号分隔；复式存整个号码池（双色球可到 20 个），3D/排列3 为 3 位''',
    'DO 0');
PREPARE pick_red_stmt FROM @pick_red_ddl; EXECUTE pick_red_stmt; DEALLOCATE PREPARE pick_red_stmt;

SET @pick_blue_len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usr_pick' AND COLUMN_NAME = 'blue_numbers');
SET @pick_blue_ddl = IF(@pick_blue_len IS NOT NULL AND @pick_blue_len < 128,
    'ALTER TABLE `usr_pick` MODIFY COLUMN `blue_numbers` VARCHAR(128) NULL COMMENT ''副号码：双色球蓝球 / 大乐透后区；复式存整个号码池''',
    'DO 0');
PREPARE pick_blue_stmt FROM @pick_blue_ddl; EXECUTE pick_blue_stmt; DEALLOCATE PREPARE pick_blue_stmt;

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


-- =====================================================================
-- 第四部分：财务金额与积分兑换站内权益
-- =====================================================================

-- 财务真实金额 + 积分兑换站内权益
-- 合规说明：兑换物为站内权益（会员/实物礼品），不涉及现金出款，
--           不改变需求文档 12.2「积分不可提现、不可反向兑换人民币」的红线。

-- ---------- 1. 会员到期时间 ----------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'vip_expire_at');
SET @ddl := IF(@col = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `vip_expire_at` DATETIME NULL COMMENT ''会员到期时间'' AFTER `credit_score`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 权益商品 ----------
CREATE TABLE IF NOT EXISTS `acc_reward_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `item_code`       VARCHAR(32)   NOT NULL,
  `name`            VARCHAR(64)   NOT NULL,
  `category`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1会员 2实物礼品',
  `points_cost`     BIGINT        NOT NULL COMMENT '兑换所需积分',
  `value_fen`       INT           NOT NULL DEFAULT 0 COMMENT '折算价值(分)，兑换时快照进订单',
  `duration_days`   INT           NOT NULL DEFAULT 0 COMMENT '会员天数，非会员类为 0',
  `stock`           INT           NOT NULL DEFAULT -1 COMMENT '-1 表示不限量',
  `exchanged_count` INT           NOT NULL DEFAULT 0,
  `description`     VARCHAR(500)  NULL,
  `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `sort_no`         INT           NOT NULL DEFAULT 0,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`item_code`),
  KEY `idx_status_sort` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换权益商品';

-- ---------- 3. 兑换单 ----------
CREATE TABLE IF NOT EXISTS `acc_reward_order` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`      VARCHAR(32)   NOT NULL,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `item_id`       BIGINT UNSIGNED NOT NULL,
  `item_name`     VARCHAR(64)   NOT NULL COMMENT '快照，商品改名不影响历史单',
  `category`      TINYINT       NOT NULL,
  `points_cost`   BIGINT        NOT NULL COMMENT '实扣积分',
  `value_fen`     INT           NOT NULL DEFAULT 0,
  `quantity`      INT           NOT NULL DEFAULT 1,
  `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1待发放 2已发放 3已取消',
  `contact_name`  VARCHAR(32)   NULL,
  `contact_phone` VARCHAR(20)   NULL,
  `address`       VARCHAR(200)  NULL,
  `issued_at`     DATETIME      NULL,
  `issue_remark`  VARCHAR(200)  NULL,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`order_no`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换单';

-- ---------- 4. 配置项 ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `value_type`, `remark`, `editable`) VALUES
('reward.exchange.enabled', '1',    'BOOL',    '积分兑换权益总开关（0关闭后前台不展示入口）', 1),
('reward.exchange.min.points', '100', 'INT',   '单次兑换最低积分门槛', 1),
('reward.exchange.daily.limit', '5', 'INT',    '每人每日可兑换次数上限', 1),
('point.to.fen.rate', '10',         'INT',     '1元兑换积分数量，财务折算金额用（须与充值比例一致）', 1),
('finance.money.currency', 'CNY',   'STRING',  '财务展示币种', 1)
ON DUPLICATE KEY UPDATE `remark` = VALUES(`remark`);

-- ---------- 5. 初始权益商品 ----------
INSERT INTO `acc_reward_item`
  (`item_code`, `name`, `category`, `points_cost`, `value_fen`, `duration_days`, `stock`, `description`, `status`, `sort_no`) VALUES
('VIP_30',   '会员 30 天',   1, 100,  800,  30, -1, '获得 30 天会员标识，个人主页与预测详情展示会员徽章', 1, 10),
('VIP_90',   '会员 90 天',   1, 260, 2000,  90, -1, '获得 90 天会员标识，折合单月更划算', 1, 20),
('VIP_365',  '会员 365 天',  1, 900, 6800, 365, -1, '获得 365 天会员标识，全年有效', 1, 30),
('GIFT_BOOK','数据分析手册', 2, 300, 1500,   0, 50, '实体印刷册，兑换后由客服联系确认收货信息并安排寄送', 1, 40),
('GIFT_NOTE','定制笔记本',   2, 200,  900,   0, 100,'平台定制笔记本，兑换后由客服联系确认收货信息并安排寄送', 1, 50)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);


-- =====================================================================
-- 第五部分：随机预测会员分级与每日免费次数
-- =====================================================================

-- 随机预测：会员分级 + 每日免费试用次数

CREATE TABLE IF NOT EXISTS `rnd_daily_usage` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `use_date`   DATE         NOT NULL,
  `used_count` INT          NOT NULL DEFAULT 0 COMMENT '当日已使用会员方法次数',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `use_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随机预测会员方法每日用量';

INSERT INTO `sys_config` (`config_key`, `config_value`, `value_type`, `remark`, `editable`) VALUES
('random.vip.daily.free', '3', 'INT', '非会员每天可使用会员专享预测方式的免费次数', 1),
('random.vip.enabled', '1',    'BOOL', '会员分级开关（0 关闭后所有方式对所有人开放）', 1)
ON DUPLICATE KEY UPDATE `remark` = VALUES(`remark`);
