package com.ttdemo.modules.random.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 随机预测会员方法每日用量。
 * 注意：不要用 INSERT ... ON DUPLICATE KEY UPDATE 的返回值做「是否成功」判断——
 * JDBC 默认 useAffectedRows=false 时返回的是匹配行数而非变更行数，值为未变化时仍返回 1。
 * 这里改为「先确保行存在，再条件 UPDATE」两步，影响行数语义明确。
 */
@Mapper
public interface RndDailyUsageMapper {

    /** 确保当日记录存在（已存在则忽略） */
    @Insert("INSERT IGNORE INTO rnd_daily_usage (user_id, use_date, used_count) VALUES (#{userId}, #{date}, 0)")
    int ensureRow(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 条件自增：返回 1 表示消耗成功，0 表示已达当日上限 */
    @Update("UPDATE rnd_daily_usage SET used_count = used_count + 1 "
            + "WHERE user_id = #{userId} AND use_date = #{date} AND used_count < #{limit}")
    int takeOne(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("limit") int limit);

    @Select("SELECT used_count FROM rnd_daily_usage WHERE user_id = #{userId} AND use_date = #{date}")
    Integer usedCount(@Param("userId") Long userId, @Param("date") LocalDate date);
}
