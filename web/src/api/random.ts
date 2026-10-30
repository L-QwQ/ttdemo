import { request } from './http'

export interface RandomMethodVO {
  code: string
  name: string
  description: string
  available: boolean
  dataNote: string | null
  /** BASIC / STATS / MYSTIC */
  group: string
  groupName: string
  groupSubtitle: string
  /** 会员专享 */
  vip: boolean
  /** 非会员且今日免费次数已用完 */
  locked: boolean
  /** 会员方法每日免费次数，-1 表示不限 */
  freeQuota: number
  usedToday: number
  remainToday: number
}

export interface RandomQuota {
  vip: boolean
  gradeEnabled: boolean
  freeQuota: number
  usedToday: number
  remainToday: number
  /** 今日干支与建除神 */
  almanac: string
}

export interface RandomGroup {
  key: string
  name: string
  numbers: string[]
}

export interface RandomMetrics {
  sum: number
  span: number
  oddEven: string
  bigSmall: string
}

export interface RandomItem {
  itemType: string
  itemValue: string
  itemLabel: string
  isFree: boolean
  weight: number
}

export interface RandomSet {
  method: string
  methodName: string
  reason: string
  groups: RandomGroup[]
  metrics: RandomMetrics
  items: RandomItem[]
}

export interface HistoryRow {
  issueNo: string
  drawTime: string | null
  redNumbers: string | null
  blueNumbers: string | null
  dataSource: number | null
  sourceName: string
}

export interface HistorySummary {
  typeCode: string
  typeName: string
  total: number
  autoSyncCount: number
  manualCount: number
  latestIssue: string | null
  latestDrawTime: string | null
  list: HistoryRow[]
}

export interface RandomResult {
  typeCode: string
  typeName: string
  requestedHistorySize: number
  historySize: number
  autoSyncCount: number
  manualCount: number
  dataNote: string
  metrics: RandomMetrics
  sets: RandomSet[]
  historyRows: HistoryRow[]
  disclaimer: string
}

export interface GenerateParams {
  typeCode: string
  method: string
  count?: number
  historySize?: number
  sumMin?: number
  sumMax?: number
  seed?: number
}

export const randomApi = {
  methods(typeCode?: string) {
    return request<RandomMethodVO[]>({ url: '/v1/random/methods', method: 'get', params: { typeCode } })
  },
  /** 会员状态与今日剩余免费次数 */
  quota() {
    return request<RandomQuota>({ url: '/v1/random/quota', method: 'get' })
  },
  generate(params: GenerateParams) {
    return request<RandomResult>({ url: '/v1/random/generate', method: 'post', data: params })
  },
  history(typeCode: string, limit = 50) {
    return request<HistorySummary>({ url: '/v1/random/history', method: 'get', params: { typeCode, limit } })
  },
}
