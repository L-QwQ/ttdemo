export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  hasNext: boolean
}

export type UserRole = 'USER' | 'ADMIN' | 'OPERATOR' | 'AUDITOR' | 'FINANCE'

/** 可进入管理后台的角色（与后端 SecurityConfig 保持一致） */
export const STAFF_ROLES: UserRole[] = ['ADMIN', 'OPERATOR', 'AUDITOR', 'FINANCE']

export interface UserVO {
  id: number
  phone: string
  nickname: string
  avatar: string
  role: UserRole
  status: number
  realnameStatus?: number
  creatorStatus?: number
  riskStatus?: number
  creditScore?: number
  /** 是否已设置登录密码；验证码自动注册的账号为 false */
  hasPassword?: boolean
  /** 会员到期时间，空或已过期即非会员 */
  vipExpireAt?: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

export type SmsScene = 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD'

export interface SmsSendResult {
  resendInterval: number
  expireIn: number
  debugCode: string | null
}

export interface LoginLog {
  id: number
  userId: number | null
  phone: string
  type: string
  success: number
  message: string | null
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface AdminStats {
  totalUsers: number
  todayNewUsers: number
  enabledUsers: number
  disabledUsers: number
  todayLogins: number
}

/** ------------------------- 通知公告 ------------------------- */

export type NoticeType = 'ANNOUNCEMENT' | 'NOTICE'
export type NoticeLevel = 'NORMAL' | 'IMPORTANT' | 'URGENT'
export type NoticeTargetType = 'ALL' | 'SPECIFIC'
export type NoticeStatus = 0 | 1

export interface MyNotice {
  id: number
  title: string
  type: NoticeType
  level: NoticeLevel
  publisherName: string | null
  publishedAt: string | null
  readAt: string | null
}

export interface UserNoticeDetail {
  id: number
  title: string
  content: string
  type: NoticeType
  level: NoticeLevel
  publisherName: string | null
  publishedAt: string | null
  readAt: string | null
}

export interface AdminNotice {
  id: number
  title: string
  summary: string
  type: NoticeType
  level: NoticeLevel
  targetType: NoticeTargetType
  status: NoticeStatus
  publisherName: string | null
  publishedAt: string | null
  createdAt: string
  receiverCount: number
  readCount: number
}

export interface AdminNoticeDetail extends Omit<AdminNotice, 'summary'> {
  content: string
  receivers: UserVO[]
}

export interface NoticeSavePayload {
  title: string
  content: string
  type: NoticeType
  level: NoticeLevel
  targetType: NoticeTargetType
  targetUserIds?: number[]
  publishNow?: boolean
}

/** ------------------------- 用户反馈 ------------------------- */

export type FeedbackType = 'BUG' | 'FEATURE' | 'EXPERIENCE' | 'OTHER'
export type FeedbackStatus = 0 | 1 | 2 | 3

export interface Feedback {
  id: number
  userId: number
  userPhone: string
  type: FeedbackType
  title: string
  content: string
  contact: string | null
  status: FeedbackStatus
  reply: string | null
  repliedAt: string | null
  createdAt: string
}
