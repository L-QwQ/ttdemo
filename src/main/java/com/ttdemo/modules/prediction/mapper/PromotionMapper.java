package com.ttdemo.modules.prediction.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 置顶推广流水（轻量写入，无实体）
 */
@Mapper
public interface PromotionMapper {

    @Insert("INSERT INTO ord_promotion (prediction_id, creator_id, user_id, points_cost, days, start_at, end_at) "
            + "VALUES (#{predictionId}, #{creatorId}, #{userId}, #{pointsCost}, #{days}, #{startAt}, #{endAt})")
    int insert(@Param("predictionId") Long predictionId,
               @Param("creatorId") Long creatorId,
               @Param("userId") Long userId,
               @Param("pointsCost") Long pointsCost,
               @Param("days") Integer days,
               @Param("startAt") LocalDateTime startAt,
               @Param("endAt") LocalDateTime endAt);
}
