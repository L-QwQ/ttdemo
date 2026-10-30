package com.ttdemo.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ttdemo.modules.message.entity.NotMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotMessageMapper extends BaseMapper<NotMessage> {

    /** 未读数按类型分组，一次查询代替 N 次 count */
    @Select("SELECT msg_type, COUNT(*) AS cnt FROM not_message "
            + "WHERE user_id = #{userId} AND is_read = 0 GROUP BY msg_type")
    List<Map<String, Object>> countUnreadByType(@Param("userId") Long userId);
}
