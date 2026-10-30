package com.ttdemo.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.admin.dto.AdminStatsVO;
import com.ttdemo.modules.admin.dto.AdminUserQuery;
import com.ttdemo.modules.admin.service.AdminUserService;
import com.ttdemo.modules.log.entity.SysLoginLog;
import com.ttdemo.modules.log.mapper.SysLoginLogMapper;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final List<String> ROLES = List.of(SysUser.ROLE_USER, SysUser.ROLE_ADMIN);

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final UserService userService;

    public AdminUserServiceImpl(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper, UserService userService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.userService = userService;
    }

    @Override
    public PageResult<UserVO> page(AdminUserQuery query) {
        String keyword = query.getKeyword();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getPhone, keyword)
                        .or()
                        .like(SysUser::getNickname, keyword))
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getRole()), SysUser::getRole, query.getRole())
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<UserVO> records = result.getRecords().stream().map(UserVO::from).toList();
        return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), records);
    }

    @Override
    public UserVO detail(Long id) {
        SysUser user = userService.getById(id);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    public void updateStatus(Long operatorId, Long userId, Integer status) {
        if (status == null || (status != SysUser.STATUS_ENABLED && status != SysUser.STATUS_DISABLED)) {
            throw new BizException(ResultCode.BAD_REQUEST, "状态值只能是 0 或 1");
        }
        SysUser user = requireUser(userId);
        if (user.getId().equals(operatorId) && status == SysUser.STATUS_DISABLED) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能禁用当前登录账号");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getStatus, status));
    }

    @Override
    public void updateRole(Long operatorId, Long userId, String role) {
        if (!ROLES.contains(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色只能是 USER 或 ADMIN");
        }
        SysUser user = requireUser(userId);
        if (user.getId().equals(operatorId) && !SysUser.ROLE_ADMIN.equals(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能降级当前登录账号");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getRole, role));
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        userService.resetPassword(userId, newPassword);
    }

    @Override
    public void delete(Long operatorId, Long userId) {
        if (userId.equals(operatorId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除当前登录账号");
        }
        requireUser(userId);
        userMapper.deleteById(userId);
    }

    @Override
    public AdminStatsVO stats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = userMapper.selectCount(null);
        long todayNew = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreatedAt, todayStart));
        long enabled = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, SysUser.STATUS_ENABLED));
        long todayLogins = loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getSuccess, 1)
                .ge(SysLoginLog::getCreatedAt, todayStart));
        return new AdminStatsVO(total, todayNew, enabled, total - enabled, todayLogins);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }
}
