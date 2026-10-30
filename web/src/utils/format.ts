export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function maskPhone(phone: string | null | undefined): string {
  if (!phone || phone.length < 7) return phone ?? '-'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

/** 分 → 元（保留两位小数，带千分位） */
export function formatFen(fen: number | null | undefined): string {
  const value = (fen ?? 0) / 100
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 分 → ￥ 字符串 */
export function formatYuan(fen: number | null | undefined): string {
  return `¥${formatFen(fen)}`
}
