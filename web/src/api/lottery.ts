import { request } from './http'
import type { Issue, LotteryType } from '@/types/lottery'

export const lotteryApi = {
  types() {
    return request<LotteryType[]>({ url: '/v1/lottery/types', method: 'get' })
  },
  issues(params: { typeCode?: string; drawStatus?: number; limit?: number }) {
    return request<Issue[]>({ url: '/v1/lottery/issues', method: 'get', params })
  },
  current(typeCode?: string) {
    return request<Issue[]>({ url: '/v1/lottery/issues/current', method: 'get', params: { typeCode } })
  },
  issue(id: number) {
    return request<Issue>({ url: `/v1/lottery/issues/${id}`, method: 'get' })
  },
}
