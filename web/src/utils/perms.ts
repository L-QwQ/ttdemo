/**
 * 前端权限点镜像：仅用于隐藏无权菜单/按钮，真正的拦截在后端 @RequirePerm。
 * 与 `com.ttdemo.security.perm.PermService` 的 ROLE_PERMS 保持一致，改动时请同步。
 */

export const PERMS = {
  PREDICTION_AUDIT: 'prd:prediction:audit',
  PREDICTION_OFFLINE: 'prd:prediction:offline',
  CREATOR_AUDIT: 'prd:creator:audit',
  ISSUE_MANAGE: 'prd:issue:manage',
  ISSUE_SETTLE: 'prd:issue:settle',
  POINT_VIEW: 'fin:point:view',
  POINT_ADJUST: 'fin:point:adjust',
  SETTLEMENT_CONFIRM: 'fin:settlement:confirm',
  RANK_REBUILD: 'rank:rebuild',
  RISK_MANAGE: 'risk:manage',
  CONFIG_MANAGE: 'sys:config:manage',
  ROLE_GRANT: 'sys:role:grant',
  USER_VIEW: 'sys:user:view',
  USER_MANAGE: 'sys:user:manage',
  NOTICE_MANAGE: 'sys:notice:manage',
  FEEDBACK_MANAGE: 'sys:feedback:manage',
  DASHBOARD_VIEW: 'sys:dashboard:view',
} as const

export type Perm = (typeof PERMS)[keyof typeof PERMS]

const ALL: Perm[] = Object.values(PERMS)

export const ROLE_PERMS: Record<string, Perm[]> = {
  ADMIN: ALL,
  OPERATOR: [
    PERMS.PREDICTION_AUDIT, PERMS.PREDICTION_OFFLINE, PERMS.CREATOR_AUDIT,
    PERMS.ISSUE_MANAGE, PERMS.ISSUE_SETTLE, PERMS.RANK_REBUILD, PERMS.CONFIG_MANAGE,
    PERMS.POINT_VIEW, PERMS.RISK_MANAGE, PERMS.USER_VIEW,
    PERMS.NOTICE_MANAGE, PERMS.FEEDBACK_MANAGE, PERMS.DASHBOARD_VIEW,
  ],
  AUDITOR: [
    PERMS.PREDICTION_AUDIT, PERMS.PREDICTION_OFFLINE, PERMS.CREATOR_AUDIT,
    PERMS.RISK_MANAGE, PERMS.USER_VIEW, PERMS.DASHBOARD_VIEW,
  ],
  FINANCE: [
    PERMS.POINT_VIEW, PERMS.POINT_ADJUST, PERMS.SETTLEMENT_CONFIRM,
    PERMS.ISSUE_SETTLE, PERMS.USER_VIEW, PERMS.DASHBOARD_VIEW,
  ],
  CREATOR: [],
  USER: [],
}

export function hasPerm(role: string | undefined | null, perm: string): boolean {
  if (!role) return false
  return (ROLE_PERMS[role] ?? []).includes(perm as Perm)
}
