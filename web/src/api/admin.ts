import { request } from './http'
import type {
  AdminNotice,
  AdminNoticeDetail,
  AdminStats,
  Feedback,
  LoginLog,
  NoticeSavePayload,
  PageResult,
  TokenResponse,
  UserRole,
  UserVO,
} from '@/types'

export interface AdminUserQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number | null
  role?: string
}

export const adminApi = {
  login(payload: { phone: string; password: string }) {
    return request<TokenResponse>({ url: '/admin/auth/login', method: 'post', data: payload })
  },
  me() {
    return request<UserVO>({ url: '/admin/auth/me', method: 'get' })
  },
  users(query: AdminUserQuery) {
    return request<PageResult<UserVO>>({ url: '/admin/users', method: 'get', params: query })
  },
  updateStatus(id: number, status: number) {
    return request<void>({ url: `/admin/users/${id}/status`, method: 'put', data: { status } })
  },
  updateRole(id: number, role: UserRole) {
    return request<void>({ url: `/admin/users/${id}/role`, method: 'put', data: { role } })
  },
  resetPassword(id: number, newPassword: string) {
    return request<void>({ url: `/admin/users/${id}/password`, method: 'put', data: { newPassword } })
  },
  remove(id: number) {
    return request<void>({ url: `/admin/users/${id}`, method: 'delete' })
  },
  stats() {
    return request<AdminStats>({ url: '/admin/stats/overview', method: 'get' })
  },
  platformStats() {
    return request<{
      onlinePredictions: number
      pendingAuditPredictions: number
      settledPredictions: number
      totalCreators: number
      pendingRealname: number
      paidOrders: number
      drawnIssues: number
      officialDrawnIssues: number
    }>({ url: '/admin/stats/platform', method: 'get' })
  },
  logs(query: { page?: number; size?: number; phone?: string; success?: number | null }) {
    return request<PageResult<LoginLog>>({ url: '/admin/logs', method: 'get', params: query })
  },

  /* ------------------------- 通知公告 ------------------------- */
  notices(query: { page?: number; size?: number; keyword?: string; type?: string; status?: number | null }) {
    return request<PageResult<AdminNotice>>({ url: '/admin/notices', method: 'get', params: query })
  },
  noticeDetail(id: number) {
    return request<AdminNoticeDetail>({ url: `/admin/notices/${id}`, method: 'get' })
  },
  createNotice(payload: NoticeSavePayload) {
    return request<number>({ url: '/admin/notices', method: 'post', data: payload })
  },
  updateNotice(id: number, payload: NoticeSavePayload) {
    return request<void>({ url: `/admin/notices/${id}`, method: 'put', data: payload })
  },
  publishNotice(id: number) {
    return request<void>({ url: `/admin/notices/${id}/publish`, method: 'post' })
  },
  removeNotice(id: number) {
    return request<void>({ url: `/admin/notices/${id}`, method: 'delete' })
  },

  /* ------------------------- 用户反馈 ------------------------- */
  feedbacks(query: {
    page?: number
    size?: number
    keyword?: string
    type?: string
    status?: number | null
  }) {
    return request<PageResult<Feedback>>({ url: '/admin/feedback', method: 'get', params: query })
  },
  feedbackDetail(id: number) {
    return request<Feedback>({ url: `/admin/feedback/${id}`, method: 'get' })
  },
  replyFeedback(id: number, payload: { reply: string; status: number }) {
    return request<void>({ url: `/admin/feedback/${id}/reply`, method: 'put', data: payload })
  },
  removeFeedback(id: number) {
    return request<void>({ url: `/admin/feedback/${id}`, method: 'delete' })
  },
}
