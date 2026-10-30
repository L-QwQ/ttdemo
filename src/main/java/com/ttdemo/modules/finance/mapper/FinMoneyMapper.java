package com.ttdemo.modules.finance.mapper;

import com.ttdemo.modules.finance.dto.FinDailyRow;
import com.ttdemo.modules.finance.dto.FinFlowRow;
import com.ttdemo.modules.finance.dto.FinSumRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 真实资金台账：把「充值实收 / 权益兑换 / 创作者结算」三张业务表合并成统一流水视图。
 * 只读查询，不做写入，避免与业务表产生双写不一致。
 */
@Mapper
public interface FinMoneyMapper {

    String FLOW_UNION = """
            SELECT 'RECHARGE' AS bizType, r.order_no AS bizNo, r.user_id AS userId,
                   (r.points + r.bonus_points) AS points, r.amount_fen AS amountFen,
                   r.status AS status, IFNULL(r.pay_time, r.created_at) AS bizTime
            FROM ord_recharge_order r
            UNION ALL
            SELECT 'REWARD' AS bizType, o.order_no AS bizNo, o.user_id AS userId,
                   -o.points_cost AS points, -o.value_fen AS amountFen,
                   o.status AS status, o.created_at AS bizTime
            FROM acc_reward_order o
            UNION ALL
            SELECT 'SETTLE' AS bizType, s.settle_no AS bizNo, s.user_id AS userId,
                   s.creator_points AS points, 0 AS amountFen,
                   s.status AS status, IFNULL(s.credit_time, s.created_at) AS bizTime
            FROM acc_settlement s
            """;

    @Select("<script>SELECT t.bizType, t.bizNo, t.userId, t.points, t.amountFen, t.status, t.bizTime FROM ("
            + FLOW_UNION
            + ") t WHERE 1 = 1"
            + "<if test='from != null'> AND t.bizTime &gt;= #{from}</if>"
            + "<if test='to != null'> AND t.bizTime &lt; #{to}</if>"
            + "<if test='bizType != null and bizType != \"\"'> AND t.bizType = #{bizType}</if>"
            + " ORDER BY t.bizTime DESC, t.bizNo DESC LIMIT #{offset}, #{size}</script>")
    List<FinFlowRow> selectFlows(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("bizType") String bizType,
                                 @Param("offset") long offset,
                                 @Param("size") long size);

    @Select("<script>SELECT COUNT(*) FROM (" + FLOW_UNION + ") t WHERE 1 = 1"
            + "<if test='from != null'> AND t.bizTime &gt;= #{from}</if>"
            + "<if test='to != null'> AND t.bizTime &lt; #{to}</if>"
            + "<if test='bizType != null and bizType != \"\"'> AND t.bizType = #{bizType}</if>"
            + "</script>")
    long countFlows(@Param("from") LocalDateTime from,
                    @Param("to") LocalDateTime to,
                    @Param("bizType") String bizType);

    /** 充值实收（仅已支付 status = 2） */
    @Select("<script>SELECT IFNULL(SUM(amount_fen), 0) AS amountFen, COUNT(*) AS cnt, "
            + "IFNULL(SUM(points + bonus_points), 0) AS points "
            + "FROM ord_recharge_order WHERE status = 2"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if></script>")
    FinSumRow sumRecharge(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 权益兑换（排除已取消 status = 3）：平台承担的折算成本 */
    @Select("<script>SELECT IFNULL(SUM(points_cost), 0) AS points, IFNULL(SUM(value_fen), 0) AS amountFen, "
            + "COUNT(*) AS cnt FROM acc_reward_order WHERE status &lt;&gt; 3"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if></script>")
    FinSumRow sumExchange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 创作者结算（仅已入账 status = 2） */
    @Select("<script>SELECT IFNULL(SUM(gross_points), 0) AS grossPoints, IFNULL(SUM(platform_fee), 0) AS feePoints, "
            + "IFNULL(SUM(creator_points), 0) AS creatorPoints, COUNT(*) AS cnt "
            + "FROM acc_settlement WHERE status = 2"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if></script>")
    FinSumRow sumSettlement(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 预测成交（已支付 + 已结算订单），用于核对积分口径 */
    @Select("<script>SELECT IFNULL(SUM(price_points), 0) AS points, IFNULL(SUM(platform_fee), 0) AS feePoints, "
            + "IFNULL(SUM(creator_income), 0) AS creatorPoints, COUNT(*) AS cnt "
            + "FROM ord_prediction_order WHERE status IN (2, 5)"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if></script>")
    FinSumRow sumConsume(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 平台对用户的积分负债：全部账户可用积分之和 */
    @Select("SELECT IFNULL(SUM(available), 0) AS points, COUNT(*) AS cnt FROM acc_point_account WHERE status = 1")
    FinSumRow sumUserAvailable();

    /** 待发放的兑换单数量（实物礼品积压） */
    @Select("SELECT COUNT(*) AS cnt FROM acc_reward_order WHERE status = 1")
    FinSumRow countPendingRewards();

    /** 每日充值金额 */
    @Select("<script>SELECT DATE(IFNULL(pay_time, created_at)) AS day, IFNULL(SUM(amount_fen), 0) AS amountFen, "
            + "COUNT(*) AS cnt FROM ord_recharge_order WHERE status = 2"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if>"
            + " GROUP BY DATE(IFNULL(pay_time, created_at)) ORDER BY day ASC</script>")
    List<FinDailyRow> dailyRecharge(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 每日权益兑换成本 */
    @Select("<script>SELECT DATE(created_at) AS day, IFNULL(SUM(value_fen), 0) AS amountFen, "
            + "IFNULL(SUM(points_cost), 0) AS points, COUNT(*) AS cnt FROM acc_reward_order WHERE status &lt;&gt; 3"
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to}</if>"
            + " GROUP BY DATE(created_at) ORDER BY day ASC</script>")
    List<FinDailyRow> dailyExchange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
