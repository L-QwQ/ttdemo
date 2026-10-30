import { request } from './http'
import type { PageResult } from '@/types'
import type {
  AccuracyInfo,
  CreatorDashboard,
  CreatorProfile,
  PredictionListItem,
  RankData,
} from '@/types/lottery'

export const creatorApi = {
  submitRealname(payload: { realName: string; idCardNo: string }) {
    return request<void>({ url: '/v1/creator/realname', method: 'post', data: payload })
  },
  myRealname() {
    return request<Record<string, unknown>>({ url: '/v1/creator/realname', method: 'get' })
  },
  apply(payload: { penName: string; intro?: string; tags?: string; applyReason?: string }) {
    return request<void>({ url: '/v1/creator/apply', method: 'post', data: payload })
  },
  profile() {
    return request<CreatorProfile>({ url: '/v1/creator/profile', method: 'get' })
  },
  updateProfile(payload: { penName?: string; intro?: string; tags?: string }) {
    return request<void>({ url: '/v1/creator/profile', method: 'put', data: payload })
  },
  dashboard() {
    return request<CreatorDashboard>({ url: '/v1/creator/dashboard', method: 'get' })
  },
  accuracy() {
    return request<AccuracyInfo>({ url: '/v1/creator/accuracy', method: 'get' })
  },
  home(creatorId: number) {
    return request<Record<string, unknown>>({ url: `/v1/creators/${creatorId}`, method: 'get' })
  },
  follow(creatorId: number) {
    return request<void>({ url: `/v1/creators/${creatorId}/follow`, method: 'post' })
  },
  unfollow(creatorId: number) {
    return request<void>({ url: `/v1/creators/${creatorId}/follow`, method: 'delete' })
  },
}

export const rankApi = {
  list(params: { rankType?: string; typeCode?: string; limit?: number }) {
    return request<RankData>({ url: '/v1/rank/list', method: 'get', params })
  },
}

export interface AuditRow {
  id: number
  predictionNo: string
  title: string
  typeCode: string
  issueNo: string
  penName: string
  creatorId: number
  status: number
  saleEndTime: string
  remainMinutes: number
  pricePoints: number
  summary: string
  submitRemark: string | null
  riskHit: string | null
  createdAt: string
}

export interface AdminIssue {
  id: number
  typeCode: string
  issueNo: string
  drawTime: string
  saleEndTime: string
  redNumbers: string | null
  blueNumbers: string | null
  drawStatus: number
  drawStatusName: string
  settleStatus: number
  remainSeconds: number
  dataSource: number
}

export interface SyncResult {
  typeCode: string
  typeName: string
  fetched: number
  inserted: number
  updated: number
  skipped: number
  failed: number
  message: string
  errors: string[]
}

export const marketAdminApi = {
  // 审核台
  auditQueue(params: { pageNum?: number; pageSize?: number; status?: number }) {
    return request<PageResult<AuditRow>>({ url: '/admin/v1/predictions', method: 'get', params })
  },
  audit(id: number, payload: { result: number; remark?: string; riskLevel?: number }) {
    return request<void>({ url: `/admin/v1/predictions/${id}/audit`, method: 'post', data: payload })
  },
  offline(id: number, reason: string) {
    return request<void>({ url: `/admin/v1/predictions/${id}/offline`, method: 'post', data: { reason } })
  },
  // 期次
  issues(params: {
    pageNum?: number
    pageSize?: number
    typeCode?: string
    drawStatus?: number
    dataSource?: number
    keyword?: string
    officialOnly?: boolean
  }) {
    return request<PageResult<AdminIssue>>({ url: '/admin/v1/issues', method: 'get', params })
  },
  createIssue(payload: { typeCode: string; issueNo: string; drawTime: string; saleEndTime?: string; saleEndOffsetMin?: number }) {
    return request<{ id: number }>({ url: '/admin/v1/issues', method: 'post', data: payload })
  },
  prepareDraw(id: number, payload: { redNumbers: string; blueNumbers?: string }) {
    return request<{ reviewTicket: string; hint: string }>({
      url: `/admin/v1/issues/${id}/draw-result/prepare`,
      method: 'post',
      data: payload,
    })
  },
  confirmDraw(id: number, payload: { redNumbers: string; blueNumbers?: string; reviewTicket: string }) {
    return request<void>({ url: `/admin/v1/issues/${id}/draw-result`, method: 'post', data: payload })
  },
  settle(id: number) {
    return request<Record<string, number>>({ url: `/admin/v1/issues/${id}/settle`, method: 'post' })
  },
  // 官方开奖数据同步
  syncDraw(typeCode: string, count = 100) {
    return request<SyncResult>({ url: `/admin/v1/issues/sync?typeCode=${typeCode}&count=${count}`, method: 'post' })
  },
  syncAllDraw(count = 100) {
    return request<SyncResult[]>({ url: `/admin/v1/issues/sync-all?count=${count}`, method: 'post' })
  },
  cleanupIssues() {
    return request<{ deleted: number; skipped: number }>({ url: '/admin/v1/issues/cleanup', method: 'post' })
  },
  ensureUpcoming() {
    return request<AdminIssue[]>({ url: '/admin/v1/issues/ensure-upcoming', method: 'post' })
  },
  // 创作者审核
  realnames(params: { pageNum?: number; pageSize?: number; auditStatus?: number; keyword?: string }) {
    return request<PageResult<Record<string, any>>>({ url: '/admin/v1/realnames', method: 'get', params })
  },
  realnameStats() {
    return request<{ pending: number; passed: number; rejected: number }>({
      url: '/admin/v1/realnames/stats',
      method: 'get',
    })
  },
  auditRealname(id: number, payload: { result: number; remark?: string }) {
    return request<void>({ url: `/admin/v1/realnames/${id}/audit`, method: 'post', data: payload })
  },
  resetRealname(id: number, remark?: string) {
    return request<void>({ url: `/admin/v1/realnames/${id}/reset`, method: 'post', data: { remark } })
  },
  creators(params: { pageNum?: number; pageSize?: number; status?: number; keyword?: string }) {
    return request<PageResult<Record<string, unknown>>>({ url: '/admin/v1/creators', method: 'get', params })
  },
  auditCreator(id: number, payload: { result: number; remark?: string }) {
    return request<void>({ url: `/admin/v1/creators/${id}/audit`, method: 'post', data: payload })
  },
  // 财务
  reconciliation(params: { startDate?: string; endDate?: string; typeCode?: string }) {
    return request<Record<string, unknown>>({ url: '/admin/v1/finance/reconciliation', method: 'get', params })
  },
  settlements(params: { pageNum?: number; pageSize?: number; status?: number }) {
    return request<PageResult<Record<string, unknown>>>({ url: '/admin/v1/settlements', method: 'get', params })
  },
  configs() {
    return request<Array<Record<string, unknown>>>({ url: '/admin/v1/configs', method: 'get' })
  },
  updateConfig(key: string, value: string) {
    return request<void>({ url: '/admin/v1/configs', method: 'put', data: { key, value } })
  },
  /** 人工调账（正负由 increase 控制） */
  adjustPoints(userId: number, payload: { points: number; increase: boolean; reason: string }) {
    return request<Record<string, number>>({
      url: `/admin/v1/users/${userId}/points/adjust`,
      method: 'post',
      data: payload,
    })
  },
  rebuildRank(rankType: string, typeCode = 'ALL') {
    return request<Record<string, number>>({
      url: `/admin/v1/rank/rebuild?rankType=${rankType}&typeCode=${typeCode}`,
      method: 'post',
      data: {},
    })
  },
}
