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

/** 复式每组规格：standard 为单式一注的号码个数，min/max 为复式可选范围 */
export interface BetGroupSpec {
  key: string
  name: string
  standard: number
  min: number
  max: number
  defaultValue: number
}

export interface BetSpec {
  supported: boolean
  note: string
  unitPrice: number
  groups: BetGroupSpec[]
}

/** 本注投注信息：注数 = 各组组合数之积，金额 = 注数 × 2 元 */
export interface BetInfo {
  mode: string
  modeName: string
  betCount: number
  amount: number
  unitPrice: number
  formula: string
  label: string
}

export interface RandomSet {
  method: string
  methodName: string
  reason: string
  groups: RandomGroup[]
  metrics: RandomMetrics
  items: RandomItem[]
  bet: BetInfo
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
  /** SINGLE 单式 / MULTI 复式 */
  betMode?: string
  /** 复式主号个数 */
  multiMain?: number
  /** 复式副号个数 */
  multiSub?: number
}

export const randomApi = {
  methods(typeCode?: string) {
    return request<RandomMethodVO[]>({ url: '/v1/random/methods', method: 'get', params: { typeCode } })
  },
  /** 复式规格：每组单注个数与复式可选范围 */
  betOptions(typeCode: string) {
    return request<BetSpec>({ url: '/v1/random/bet-options', method: 'get', params: { typeCode } })
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
