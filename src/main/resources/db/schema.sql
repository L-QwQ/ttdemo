-- ============================================================
-- ttdemo 基础库结构（MySQL 8.0+，utf8mb4）
-- 执行：mysql -uroot -p < src/main/resources/db/schema.sql
-- ============================================================

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
