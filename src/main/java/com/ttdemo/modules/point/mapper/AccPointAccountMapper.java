package com.ttdemo.modules.point.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ttdemo.modules.point.entity.AccPointAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccPointAccountMapper extends BaseMapper<AccPointAccount> {

    /** 扣减可用积分：不带 version 条件，靠行锁串行 + 影响行数判断（文档 6.8.1 二选一） */
    @Update("UPDATE acc_point_account SET available = available - #{points}, "
            + "total_consume = total_consume + #{points}, version = version + 1 "
            + "WHERE user_id = #{userId} AND available >= #{points} AND status = 1")
    int deduct(@Param("userId") Long userId, @Param("points") long points);

    /** 增加可用积分（incomeDelta/rechargeDelta 分别累计到 total_income / total_recharge） */
    @Update("UPDATE acc_point_account SET available = available + #{points}, "
            + "total_income = total_income + #{incomeDelta}, "
            + "total_recharge = total_recharge + #{rechargeDelta}, version = version + 1 "
            + "WHERE user_id = #{userId} AND status = 1")
    int increase(@Param("userId") Long userId, @Param("points") long points,
                 @Param("incomeDelta") long incomeDelta, @Param("rechargeDelta") long rechargeDelta);

    /** 过期扣减：只减可用与总额，累计过期增加 */
    @Update("UPDATE acc_point_account SET available = available - #{points}, "
            + "total_expired = total_expired + #{points}, version = version + 1 "
            + "WHERE user_id = #{userId} AND available >= #{points}")
    int expire(@Param("userId") Long userId, @Param("points") long points);
}
