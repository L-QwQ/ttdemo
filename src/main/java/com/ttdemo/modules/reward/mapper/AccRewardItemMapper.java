package com.ttdemo.modules.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ttdemo.modules.reward.entity.AccRewardItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccRewardItemMapper extends BaseMapper<AccRewardItem> {

    /** 扣库存并累加兑换次数；stock = -1 表示不限量，不参与库存判断 */
    @Update("UPDATE acc_reward_item SET exchanged_count = exchanged_count + #{count}, "
            + "stock = CASE WHEN stock < 0 THEN stock ELSE stock - #{count} END "
            + "WHERE id = #{id} AND status = 1 "
            + "AND (stock < 0 OR stock >= #{count})")
    int takeStock(@Param("id") Long id, @Param("count") int count);

    /** 取消兑换时归还库存（不限量商品不动 stock） */
    @Update("UPDATE acc_reward_item SET exchanged_count = GREATEST(exchanged_count - #{count}, 0), "
            + "stock = CASE WHEN stock < 0 THEN stock ELSE stock + #{count} END "
            + "WHERE id = #{id}")
    int returnStock(@Param("id") Long id, @Param("count") int count);
}
