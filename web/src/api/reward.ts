import { request } from './http'
import type { PageResult } from '@/types'

export interface RewardItem {
  id: number
  itemCode: string
  name: string
  category: number
  categoryName: string
  pointsCost: number
  valueFen: number
  durationDays: number
  stock: number
  exchangedCount: number
  description: string | null
  status: number
  sortNo: number
  unlimited: boolean
  soldOut: boolean
  affordable: boolean
}

export interface RewardSummary {
  available: number
  totalIncome: number
  totalRecharge: number
  totalConsume: number
  vip: boolean
  vipExpireAt: string | null
  exchangedPoints: number
  exchangedCount: number
  itemCount: number
  pendingCount: number
  exchangeEnabled: boolean
  minPoints: number
  dailyLimit: number
  todayCount: number
}

export interface RewardOrder {
  id: number
  orderNo: string
  userId: number
  userPhone?: string | null
  userNickname?: string | null
  itemId: number
  itemName: string
  category: number
  pointsCost: number
  valueFen: number
  quantity: number
  status: number
  statusName: string
  contactName?: string | null
  contactPhone?: string | null
  address?: string | null
  issuedAt?: string | null
  issueRemark?: string | null
  createdAt: string
}

export interface ExchangePayload {
  itemId: number
  quantity?: number
  contactName?: string
  contactPhone?: string
  address?: string
}

export const rewardApi = {
  items() {
    return request<RewardItem[]>({ url: '/v1/rewards/items', method: 'get' })
  },
  summary() {
    return request<RewardSummary>({ url: '/v1/rewards/summary', method: 'get' })
  },
  exchange(payload: ExchangePayload) {
    return request<RewardOrder>({ url: '/v1/rewards/orders', method: 'post', data: payload })
  },
  myOrders(params: { pageNum?: number; pageSize?: number }) {
    return request<PageResult<RewardOrder>>({ url: '/v1/rewards/orders', method: 'get', params })
  },
}

/** 后台：权益商品与兑换单 */
export const rewardAdminApi = {
  items() {
    return request<RewardItem[]>({ url: '/admin/v1/rewards/items', method: 'get' })
  },
  createItem(payload: Partial<RewardItem> & { itemCode: string; name: string; pointsCost: number }) {
    return request<RewardItem>({ url: '/admin/v1/rewards/items', method: 'post', data: payload })
  },
  updateItem(id: number, payload: Partial<RewardItem>) {
    return request<RewardItem>({ url: `/admin/v1/rewards/items/${id}`, method: 'put', data: payload })
  },
  orders(params: { pageNum?: number; pageSize?: number; status?: number; keyword?: string }) {
    return request<PageResult<RewardOrder>>({ url: '/admin/v1/rewards/orders', method: 'get', params })
  },
  issue(id: number, remark?: string) {
    return request<void>({ url: `/admin/v1/rewards/orders/${id}/issue`, method: 'post', data: { remark } })
  },
  cancel(id: number, remark: string) {
    return request<void>({ url: `/admin/v1/rewards/orders/${id}/cancel`, method: 'post', data: { remark } })
  },
}
