package com.ttdemo.modules.user.service;

import com.ttdemo.modules.user.dto.UpdateProfileRequest;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.entity.SysUser;

import java.util.Collection;
import java.util.List;
import com.ttdemo.common.api.PageResult;
import java.util.Map;

/**
 * 用户领域服务：只做用户本身的读写，不处理令牌与验证码
 */
public interface UserService {

    SysUser getById(Long id);

    SysUser getByPhone(String phone);

    /**
     * 创建用户；rawPassword 为空表示不设置密码（只能用验证码登录）
     */
    SysUser create(String phone, String rawPassword, String nickname, String role);

    void updateLastLogin(Long userId);

    UserVO getProfile(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 首次设置密码：验证码自动注册的账号没有原密码，凭手机验证码直接设置。
     * 已设置过密码的账号不允许走此通道。
     */
    void initPassword(Long userId, String code, String newPassword);

    void resetPassword(Long userId, String newPassword);

    /**
     * 累加会员天数（原会员未过期则顺延，已过期从当前时间起算），返回新的到期时间
     */
    java.time.LocalDateTime grantVipDays(Long userId, int days);

    /**
     * 是否会员（会员到期时间晚于当前时间）
     */
    boolean isVip(Long userId);

    /** 记录协议同意（版本取配置当前值） */
    void recordAgreement(Long userId);

    /** 申请注销：登记剩余积分与按充值比例折算的可退金额 */
    Long applyDeactivation(Long userId, String reason);

    /** 我的注销申请（无则 null） */
    Map<String, Object> myDeactivation(Long userId);

    /** 后台：注销申请分页 */
    PageResult<Map<String, Object>> listDeactivations(
            long pageNum, long pageSize, Integer status);

    /** 后台：审批注销，通过则扣减剩余积分、停用账号并登记退款 */
    void handleDeactivation(Long id, Long operatorId, boolean approve, String remark);

    /** 确保用户有邀请码并返回（无则生成） */
    String ensureInviteCode(Long userId);

    /** 按邀请码查用户（用于绑定邀请关系） */
    SysUser getByInviteCode(String inviteCode);

    /** 绑定邀请人（仅首次注册时调用，已有邀请人则不覆盖） */
    void bindInviter(Long userId, Long inviterId);

    /** 我邀请的人数 */
    long countInvited(Long inviterId);

    /** 记录一次密码登录失败，达到阈值时写入锁定时间 */
    java.time.LocalDateTime recordLoginFail(Long userId, int maxFail, int lockMinutes);

    /** 登录成功后清零失败计数与锁定 */
    void clearLoginFail(Long userId);

    /**
     * 全部启用状态的用户 id，供全员通知展开接收人使用
     */
    List<Long> listEnabledUserIds();

    /**
     * 过滤出真实存在且启用的用户 id（去重），供指定人群通知使用
     */
    List<Long> filterEnabledUserIds(Collection<Long> ids);

    /**
     * 按 id 批量查询用户简要信息（用于通知接收人回显）
     */
    List<UserVO> listByIds(Collection<Long> ids);
}
