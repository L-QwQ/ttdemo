import { request } from './http'
import type { PageResult } from '@/types'
import type { PointAccount, PointTransaction } from '@/types/lottery'

export interface RechargeTier {
  amountFen: number
  amountYuan: number
  points: number
  rate: number
}

export interface RechargeResult {
  orderNo: string
  amountFen: number
  points: number
  bonusPoints: number
  payChannel: number
  payParams: Record<string, string>
  mockPayUrl: string | null
}

export interface RechargeStatus {
  orderNo: string
  amountFen: number
  points: number
  bonusPoints: number
  status: number
  statusName: string
  payTime: string | null
}

export const pointApi = {
  account() {
    return request<PointAccount>({ url: '/v1/points/account', method: 'get' })
  },
  transactions(params: { pageNum?: number; pageSize?: number; bizType?: string }) {
    return request<PageResult<PointTransaction>>({ url: '/v1/points/transactions', method: 'get', params })
  },
  rechargeConfig() {
    return request<RechargeTier[]>({ url: '/v1/points/recharge-config', method: 'get' })
  },
  createRecharge(amountFen: number, payChannel = 9) {
    return request<RechargeResult>({
      url: '/v1/recharge/orders',
      method: 'post',
      data: { amountFen, payChannel },
    })
  },
  rechargeStatus(orderNo: string) {
    return request<RechargeStatus>({ url: `/v1/recharge/orders/${orderNo}`, method: 'get' })
  },
  mockPay(orderNo: string) {
    return request<void>({ url: `/v1/recharge/orders/${orderNo}/mock-pay`, method: 'post', data: {} })
  },
}
