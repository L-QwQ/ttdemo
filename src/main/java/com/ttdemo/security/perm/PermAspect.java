package com.ttdemo.security.perm;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 权限点 / 创作者资格切面。
 * 统一在 service 的 controller 边界抛 40301 / 40302，Controller 内不再写角色判断。
 */
@Aspect
@Component
@Order(1)
public class PermAspect {

    private final PermService permService;
    private final SysUserMapper userMapper;

    public PermAspect(PermService permService, SysUserMapper userMapper) {
        this.permService = permService;
        this.userMapper = userMapper;
    }

    @Before("@annotation(requirePerm)")
    public void checkPerm(RequirePerm requirePerm) {
        if (!permService.has(requirePerm.value())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    @Before("@annotation(com.ttdemo.security.perm.RequireCreator)")
    public void checkCreator() {
        Long userId = com.ttdemo.security.SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!user.isCertifiedCreator()) {
            throw new BizException(ResultCode.NOT_CREATOR);
        }
        if (user.getRiskStatus() != null && user.getRiskStatus() == 2) {
            throw new BizException(ResultCode.RISK_LIMITED, "账号已被限制发布");
        }
    }
}
