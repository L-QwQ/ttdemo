/** 彩种静态映射（code → 名称/色） */
export const LOTTERY_TYPE_NAMES: Record<string, string> = {
  SSQ: '双色球',
  DLT: '大乐透',
  FC3D: '福彩3D',
  PL3: '排列3',
  KL8: '快乐8',
}

/** 号码型预测项：取值是一组号码，可以画成球 */
export const NUMBER_ITEM_TYPES = ['RED_SINGLE', 'RED_MULTI', 'DAN', 'KILL', 'BLUE_SINGLE']

export function isNumberItem(itemType: string | null | undefined): boolean {
  return !!itemType && NUMBER_ITEM_TYPES.includes(itemType)
}

const ITEM_TYPE_NAMES: Record<string, string> = {
  RED_SINGLE: '单式红球组',
  RED_MULTI: '复式红球池',
  DAN: '胆码',
  KILL: '杀号',
  BLUE_SINGLE: '蓝球/后区',
  SUM_RANGE: '和值区间',
  SPAN: '跨度',
  ODD_EVEN: '奇偶比',
  BIG_SMALL: '大小比',
  FORM_COMBO: '形态组合',
  AI_SCORE: '综合评分',
}

export function itemTypeName(itemType: string | null | undefined): string {
  if (!itemType) return '预测项'
  return ITEM_TYPE_NAMES[itemType] ?? itemType
}

/** 按位取号的彩种：一注就是几位数字，允许重复，顺序有意义 */
export function isDigitType(typeCode: string | null | undefined): boolean {
  return typeCode === 'FC3D' || typeCode === 'PL3'
}

/** 号码拆分：兼容 "03,05,12" / "3 5 12" / "3，5，12" */
export function splitNumbers(raw: string | null | undefined): string[] {
  if (!raw) return []
  return raw
    .split(/[,;\s，；、]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 号码拆成数字数组（非法片段直接丢弃） */
export function toNumbers(raw: string | null | undefined): number[] {
  return splitNumbers(raw)
    .map((s) => Number(s))
    .filter((n) => Number.isInteger(n))
}

/** 按位号码：逐字符取数字，"070" 与 "0,7,0" 等价 */
export function toDigits(raw: string | null | undefined): number[] {
  if (!raw) return []
  return raw
    .replace(/[^\d]/g, '')
    .split('')
    .map((c) => Number(c))
}

/** 统一补零展示：3 → 03 */
export function padNumber(value: number | string, width = 2): string {
  const text = String(value)
  return text.length >= width ? text : text.padStart(width, '0')
}

export interface PlayGroup {
  key: string
  name: string
  min: number
  max: number
  pickMin: number
  pickMax: number
  drawCount: number
}

const SSQ_MAIN: PlayGroup = { key: 'RED', name: '红球', min: 1, max: 33, pickMin: 1, pickMax: 20, drawCount: 6 }
const SSQ_SUB: PlayGroup = { key: 'BLUE', name: '蓝球', min: 1, max: 16, pickMin: 1, pickMax: 1, drawCount: 1 }
const DEFAULT_GROUPS: PlayGroup[] = [SSQ_MAIN, SSQ_SUB]

/**
 * 解析彩种 playConfig 的号码池。
 * 后端配置是 {"groups":[{key,name,min,max,pickMin,pickMax,drawCount}]}，
 * 缺失或损坏时退回双色球结构，避免选号器直接白屏。
 */
export function parsePlayGroups(playConfig: string | null | undefined): PlayGroup[] {
  if (!playConfig) return DEFAULT_GROUPS
  try {
    const config = JSON.parse(playConfig) as { groups?: Partial<PlayGroup>[] }
    const groups = (config.groups ?? []).map((group, index) => {
      const fallback = index === 1 ? SSQ_SUB : SSQ_MAIN
      return {
        key: group.key ?? fallback.key,
        name: group.name ?? fallback.name,
        min: group.min ?? fallback.min,
        max: group.max ?? fallback.max,
        pickMin: group.pickMin ?? 1,
        pickMax: group.pickMax ?? fallback.pickMax,
        drawCount: group.drawCount ?? fallback.drawCount,
      }
    })
    return groups.length ? groups : DEFAULT_GROUPS
  } catch {
    return DEFAULT_GROUPS
  }
}

/** 预测项对应的号码池：蓝球/后区取第二组，其余取第一组 */
export function groupForItem(groups: PlayGroup[], itemType: string): PlayGroup {
  const index = itemType === 'BLUE_SINGLE' ? 1 : 0
  return groups[index] ?? groups[0] ?? SSQ_MAIN
}

/**
 * 该预测项最多能选几个号。
 * 单式红球必须与开奖个数完全一致（否则判定永远不中）；
 * 复式红球池/胆码/杀号可以超过单注上限（快乐8 一注 10 个，复式能选到 20 个）；
 * 蓝球/后区复式是一个池，最多整池（双色球 16、大乐透 12）。
 */
export function maxPicksForItem(group: PlayGroup, itemType: string): number {
  if (itemType === 'RED_SINGLE') return group.drawCount
  if (itemType === 'BLUE_SINGLE') return Math.max(group.drawCount, group.max - group.min + 1)
  return Math.max(group.pickMax, group.drawCount)
}

export function typeName(code: string | null | undefined): string {
  if (!code) return '-'
  return LOTTERY_TYPE_NAMES[code] ?? code
}

/** remainSeconds → 倒计时文案 */
export function remainText(seconds: number | null | undefined): string {
  if (seconds == null || seconds <= 0) return '已停售'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `还剩 ${d} 天 ${h} 小时`
  if (h > 0) return `还剩 ${h} 小时 ${m} 分`
  return `还剩 ${Math.max(m, 1)} 分钟`
}

/**
 * 开奖附加信息（extra 可能是 JSON 或纯文本）→ 可读摘要
 * 后端对官方数据存的是 {"sum":106,"span":26,"oddEven":"2:4","bigSmall":"4:2"} 这类结构
 */
export function extraSummary(extra: string | null | undefined): string {
  if (!extra) return ''
  const raw = extra.trim()
  if (!raw.startsWith('{')) return raw
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    const parts: string[] = []
    if (obj.sum != null) parts.push(`和值 ${obj.sum}`)
    if (obj.span != null) parts.push(`跨度 ${obj.span}`)
    if (obj.oddEven) parts.push(`奇偶 ${obj.oddEven}`)
    if (obj.bigSmall) parts.push(`大小 ${obj.bigSmall}`)
    return parts.join(' · ')
  } catch {
    return ''
  }
}
