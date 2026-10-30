-- ============================================================
-- 随机预测：会员分级 + 每日免费试用次数
-- 幂等，执行：mysql --default-character-set=utf8mb4 -uroot -p ttdemo < V20261028__random_vip.sql
-- ============================================================

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
