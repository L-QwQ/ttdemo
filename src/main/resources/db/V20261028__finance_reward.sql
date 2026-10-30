-- ============================================================
-- 财务真实金额 + 积分兑换站内权益
-- 幂等脚本，执行：mysql -uroot -p ttdemo < V20261028__finance_reward.sql
-- 合规说明：兑换物为站内权益（会员/实物礼品），不涉及现金出款，
--           不改变需求文档 12.2「积分不可提现、不可反向兑换人民币」的红线。
-- ============================================================

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
