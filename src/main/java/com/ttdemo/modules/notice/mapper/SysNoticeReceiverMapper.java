package com.ttdemo.modules.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ttdemo.modules.notice.dto.MyNoticeVO;
import com.ttdemo.modules.notice.dto.ReceiverStat;
import com.ttdemo.modules.notice.entity.SysNoticeReceiver;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface SysNoticeReceiverMapper extends BaseMapper<SysNoticeReceiver> {

    /** 批量写入接收人，唯一键冲突直接忽略，保证发布幂等 */
    @Insert("<script>"
            + "INSERT IGNORE INTO sys_notice_receiver (notice_id, user_id) VALUES "
            + "<foreach collection='userIds' item='uid' separator=','>(#{noticeId}, #{uid})</foreach>"
            + "</script>")
    int insertBatch(@Param("noticeId") Long noticeId, @Param("userIds") Collection<Long> userIds);

    /** 我的通知分页（只取已发布且未删除的通知） */
    @Select("<script>"
            + "SELECT n.id, n.title, n.type, n.level, n.publisher_name AS publisherName, "
            + "       n.published_at AS publishedAt, r.read_at AS readAt "
            + "FROM sys_notice_receiver r "
            + "JOIN sys_notice n ON n.id = r.notice_id AND n.deleted = 0 AND n.status = 1 "
            + "WHERE r.user_id = #{userId} "
            + "<if test='unreadOnly != null and unreadOnly'>AND r.read_at IS NULL </if>"
            + "<if test='type != null and type != \"\"'>AND n.type = #{type} </if>"
            + "ORDER BY n.published_at DESC, n.id DESC"
            + "</script>")
    IPage<MyNoticeVO> selectMyNoticePage(IPage<MyNoticeVO> page,
                                         @Param("userId") Long userId,
                                         @Param("unreadOnly") Boolean unreadOnly,
                                         @Param("type") String type);

    @Select("SELECT COUNT(*) FROM sys_notice_receiver r "
            + "JOIN sys_notice n ON n.id = r.notice_id AND n.deleted = 0 AND n.status = 1 "
            + "WHERE r.user_id = #{userId} AND r.read_at IS NULL")
    long countUnread(@Param("userId") Long userId);

    /** 单条已读，仅当原本未读时才更新，返回受影响行数用于判断是否存在 */
    @Update("UPDATE sys_notice_receiver SET read_at = #{readAt} "
            + "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND read_at IS NULL")
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);

    @Update("UPDATE sys_notice_receiver r "
            + "JOIN sys_notice n ON n.id = r.notice_id AND n.deleted = 0 AND n.status = 1 "
            + "SET r.read_at = #{readAt} WHERE r.user_id = #{userId} AND r.read_at IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Select("<script>"
            + "SELECT notice_id AS noticeId, COUNT(*) AS total, "
            + "       SUM(CASE WHEN read_at IS NOT NULL THEN 1 ELSE 0 END) AS readCount "
            + "FROM sys_notice_receiver WHERE notice_id IN "
            + "<foreach collection='noticeIds' item='nid' open='(' separator=',' close=')'>#{nid}</foreach> "
            + "GROUP BY notice_id"
            + "</script>")
    List<ReceiverStat> statByNoticeIds(@Param("noticeIds") Collection<Long> noticeIds);

    @Select("SELECT user_id FROM sys_notice_receiver WHERE notice_id = #{noticeId}")
    List<Long> selectReceiverIds(@Param("noticeId") Long noticeId);
}
