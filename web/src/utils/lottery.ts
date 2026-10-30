/** 彩种静态映射（code → 名称/色） */
export const LOTTERY_TYPE_NAMES: Record<string, string> = {
  SSQ: '双色球',
  DLT: '大乐透',
  FC3D: '福彩3D',
  PL3: '排列3',
  KL8: '快乐8',
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
