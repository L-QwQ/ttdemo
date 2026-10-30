import { request } from './http'
import type { PageResult } from '@/types'
import type {
  HitInfo,
  PredictionDetail,
  PredictionListItem,
  PriceQuote,
} from '@/types/lottery'

export interface PredictionQuery {
  typeCode?: string
  issueNo?: string
  playType?: string
  onlyFree?: boolean
  priceMin?: number
  priceMax?: number
  sortBy?: string
  pageNum?: number
  pageSize?: number
}

export interface PredictionItemPayload {
  itemType: string
  itemValue: string
  itemLabel?: string
  isFree?: boolean
  weight?: number
}

export interface PredictionSavePayload {
  typeCode: string
  issueNo: string
  title: string
  playType: string
  summary: string
  contentHtml: string
  contentImages?: string[]
  pricePoints?: number
  items: PredictionItemPayload[]
}

export const predictionApi = {
  list(query: PredictionQuery) {
    return request<PageResult<PredictionListItem>>({ url: '/v1/predictions', method: 'get', params: query })
  },
  detail(id: number) {
    return request<PredictionDetail>({ url: `/v1/predictions/${id}`, method: 'get' })
  },
  content(id: number) {
    return request<{ contentHtml: string; contentImages: string[]; watermark: string; expireSeconds: number }>({
      url: `/v1/predictions/${id}/content`,
      method: 'get',
    })
  },
  hitDetail(id: number) {
    return request<HitInfo | null>({ url: `/v1/predictions/${id}/hit-detail`, method: 'get' })
  },
  create(payload: PredictionSavePayload) {
    return request<{ id: number; predictionNo: string; status: number }>({
      url: '/v1/predictions',
      method: 'post',
      data: payload,
    })
  },
  submit(id: number) {
    return request<{ status: number; auditEstimateMinutes: number }>({
      url: `/v1/predictions/${id}/submit`,
      method: 'post',
      data: {},
    })
  },
  withdraw(id: number) {
    return request<void>({ url: `/v1/predictions/${id}/withdraw`, method: 'post' })
  },
  remove(id: number) {
    return request<void>({ url: `/v1/predictions/${id}`, method: 'delete' })
  },
  mine(params: { pageNum?: number; pageSize?: number; status?: number }) {
    return request<PageResult<PredictionListItem>>({ url: '/v1/predictions/mine', method: 'get', params })
  },
  purchased(params: { pageNum?: number; pageSize?: number }) {
    return request<PageResult<PredictionListItem>>({ url: '/v1/predictions/purchased', method: 'get', params })
  },
  priceQuote(typeCode: string, playType: string) {
    return request<PriceQuote>({
      url: '/v1/predictions/price-quote',
      method: 'get',
      params: { typeCode, playType },
    })
  },
  /** 解锁下单：同一业务动作复用同一幂等键 */
  unlock(predictionId: number, idempotentKey: string) {
    return request<{ orderNo: string; pricePoints: number; balanceAfter: number; status: number }>({
      url: '/v1/orders/unlock',
      method: 'post',
      data: { predictionId },
      headers: { 'Idempotent-Key': idempotentKey, 'X-Device-Id': deviceId() },
    })
  },
}

/** 本地稳定的设备指纹（简化版：localStorage UUID + UA 摘要） */
export function deviceId(): string {
  const key = 'ttdemo.deviceId'
  let value = localStorage.getItem(key)
  if (!value) {
    value = `d-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`
    localStorage.setItem(key, value)
  }
  return value
}

export function newIdempotentKey(): string {
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
