package com.ttdemo.security.perm;

/**
 * 权限点常量：模块:资源:动作（对齐需求文档 4.2）
 */
public interface Permissions {

    String PREDICTION_PUBLISH = "prd:prediction:publish";
    String PREDICTION_AUDIT = "prd:prediction:audit";
    String PREDICTION_OFFLINE = "prd:prediction:offline";
    String CREATOR_AUDIT = "prd:creator:audit";
    String ISSUE_MANAGE = "prd:issue:manage";
    String ISSUE_SETTLE = "prd:issue:settle";
    String POINT_VIEW = "fin:point:view";
    String POINT_ADJUST = "fin:point:adjust";
    String SETTLEMENT_CONFIRM = "fin:settlement:confirm";
    String RANK_REBUILD = "rank:rebuild";
    String RISK_MANAGE = "risk:manage";
    String CONFIG_MANAGE = "sys:config:manage";
    String ROLE_GRANT = "sys:role:grant";
    String USER_VIEW = "sys:user:view";
    String USER_MANAGE = "sys:user:manage";
    String NOTICE_MANAGE = "sys:notice:manage";
    String FEEDBACK_MANAGE = "sys:feedback:manage";
    String DASHBOARD_VIEW = "sys:dashboard:view";
}
