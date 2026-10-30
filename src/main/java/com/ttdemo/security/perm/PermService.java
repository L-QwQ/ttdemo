package com.ttdemo.security.perm;

import com.ttdemo.security.LoginUser;
import com.ttdemo.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 角色 → 权限点映射。
 * 本期角色存 sys_user.role 单值，权限点在此集中定义；后续如需细粒度可迁到 DB 表。
 */
@Service
public class PermService {

    private static final Set<String> ALL = Set.of(
            Permissions.PREDICTION_PUBLISH, Permissions.PREDICTION_AUDIT, Permissions.PREDICTION_OFFLINE,
            Permissions.CREATOR_AUDIT, Permissions.ISSUE_MANAGE, Permissions.ISSUE_SETTLE,
            Permissions.POINT_VIEW, Permissions.POINT_ADJUST, Permissions.SETTLEMENT_CONFIRM,
            Permissions.RANK_REBUILD, Permissions.RISK_MANAGE, Permissions.CONFIG_MANAGE,
            Permissions.ROLE_GRANT, Permissions.USER_VIEW, Permissions.USER_MANAGE,
            Permissions.NOTICE_MANAGE, Permissions.FEEDBACK_MANAGE, Permissions.DASHBOARD_VIEW);

    /**
     * 分权原则：改角色、改用户状态、重置密码、删用户只给 ADMIN；
     * 运营负责公告与反馈；财务与审核员只读用户与概览。
     */
    private static final Map<String, Set<String>> ROLE_PERMS = Map.of(
            "ADMIN", ALL,
            "OPERATOR", Set.of(Permissions.PREDICTION_AUDIT, Permissions.PREDICTION_OFFLINE,
                    Permissions.CREATOR_AUDIT, Permissions.ISSUE_MANAGE, Permissions.ISSUE_SETTLE,
                    Permissions.RANK_REBUILD, Permissions.CONFIG_MANAGE,
                    Permissions.POINT_VIEW, Permissions.RISK_MANAGE,
                    Permissions.USER_VIEW, Permissions.NOTICE_MANAGE,
                    Permissions.FEEDBACK_MANAGE, Permissions.DASHBOARD_VIEW),
            "AUDITOR", Set.of(Permissions.PREDICTION_AUDIT, Permissions.PREDICTION_OFFLINE,
                    Permissions.CREATOR_AUDIT, Permissions.RISK_MANAGE,
                    Permissions.USER_VIEW, Permissions.DASHBOARD_VIEW),
            "FINANCE", Set.of(Permissions.POINT_VIEW, Permissions.POINT_ADJUST,
                    Permissions.SETTLEMENT_CONFIRM, Permissions.ISSUE_SETTLE,
                    Permissions.USER_VIEW, Permissions.DASHBOARD_VIEW),
            "CREATOR", Set.of(Permissions.PREDICTION_PUBLISH),
            "USER", Set.of());

    public boolean has(String role, String perm) {
        if (role == null) {
            return false;
        }
        return ROLE_PERMS.getOrDefault(role, Set.of()).contains(perm);
    }

    public boolean has(String perm) {
        LoginUser user = SecurityUtils.currentUserOrNull();
        return user != null && has(user.role(), perm);
    }
}
