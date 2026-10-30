import { request } from './http'
import type { PageResult } from '@/types'

export interface MoneyDaily {
  day: string
  rechargeFen: number
  exchangeFen: number
  netFen: number
  exchangePoints: number
}

export interface MoneyOverview {
  currency: string
  pointRate: number
  cashInFen: number
  cashOutFen: number
  rechargeCount: number
  rechargePoints: number
  consumePoints: number
  consumeFen: number
  consumeCount: number
  platformFeePoints: number
  platformFeeFen: number
  creatorIncomePoints: number
  creatorIncomeFen: number
  settleGrossPoints: number
  settleGrossFen: number
  settleCount: number
  exchangePoints: number
  exchangeFen: number
  exchangeCount: number
  pendingRewardCount: number
  netPlatformFen: number
  pointLiabilityPoints: number
  pointLiabilityFen: number
  accountCount: number
  note: string
  trend: MoneyDaily[]
}

export interface MoneyFlow {
  bizType: string
  bizTypeName: string
  bizNo: string
  userId: number | null
  userPhone: string | null
  userNickname: string | null
  points: number
  amountFen: number
  status: number
  statusName: string
  bizTime: string
}

export const financeApi = {
  overview(params: { startDate?: string; endDate?: string }) {
    return request<MoneyOverview>({ url: '/admin/v1/finance/money/overview', method: 'get', params })
  },
  flows(params: {
    startDate?: string
    endDate?: string
    bizType?: string
    pageNum?: number
    pageSize?: number
  }) {
    return request<PageResult<MoneyFlow>>({ url: '/admin/v1/finance/money/flows', method: 'get', params })
  },
}
