import { request } from './http'
import type { PageResult } from '@/types'

export interface SignInStatus {
  todaySigned: boolean
  streakDays: number
  basePoints: number
  streakBonus: number
  streakCycle: number
  todayPoints: number
}

export interface SignInResult {
  points: number
  basePoints: number
  bonusPoints: number
  streakDays: number
  balance: number
}

export interface ReportPayload {
  targetType: number
  targetId: number
  reasonType: number
  content?: string
  evidenceUrl?: string
}

export interface ReportRow {
  id: number
  reporterId: number
  reporterPhone?: string | null
  targetType: number
  targetTypeName: string
  targetId: number
  targetTitle?: string | null
  reasonType: number
  reasonName: string
  content?: string | null
  evidenceUrl?: string | null
  status: number
  statusName: string
  handleResult?: string | null
  handleTime?: string | null
  createdAt: string
}

export const growthApi = {
  signInStatus() {
    return request<SignInStatus>({ url: '/v1/growth/sign-in', method: 'get' })
  },
  signIn() {
    return request<SignInResult>({ url: '/v1/growth/sign-in', method: 'post' })
  },
}

export const reportApi = {
  submit(payload: ReportPayload) {
    return request<number>({ url: '/v1/reports', method: 'post', data: payload })
  },
  mine(params: { pageNum?: number; pageSize?: number }) {
    return request<PageResult<ReportRow>>({ url: '/v1/reports/mine', method: 'get', params })
  },
}

export const reportAdminApi = {
  page(params: { pageNum?: number; pageSize?: number; status?: number }) {
    return request<PageResult<ReportRow>>({ url: '/admin/v1/reports', method: 'get', params })
  },
  pendingCount() {
    return request<{ count: number }>({ url: '/admin/v1/reports/pending-count', method: 'get' })
  },
  handle(id: number, payload: { status: number; handleResult?: string; offline?: boolean }) {
    return request<void>({ url: `/admin/v1/reports/${id}/handle`, method: 'post', data: payload })
  },
}

export interface DeactivationRow {
  id: number
  userId: number
  phone: string
  reason?: string | null
  pointsLeft: number
  refundFen: number
  status: number
  statusName: string
  handleRemark?: string | null
  createdAt: string
}

export const deactivationApi = {
  apply(reason?: string) {
    return request<{ id: number }>({ url: '/user/deactivate', method: 'post', data: { reason } })
  },
  mine() {
    return request<Record<string, unknown> | null>({ url: '/user/deactivate', method: 'get' })
  },
}

export const deactivationAdminApi = {
  page(params: { pageNum?: number; pageSize?: number; status?: number }) {
    return request<PageResult<DeactivationRow>>({ url: '/admin/v1/deactivations', method: 'get', params })
  },
  handle(id: number, approve: boolean, remark?: string) {
    return request<void>({ url: `/admin/v1/deactivations/${id}/handle`, method: 'post', data: { approve, remark } })
  },
}
