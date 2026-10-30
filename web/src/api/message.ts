import { request } from './http'
import type { PageResult } from '@/types'

/** 站内信类型：与后端 MessageType 一致 */
export type MessageType = 'TRADE' | 'AUDIT' | 'SETTLE' | 'SYSTEM'

export interface UserMessage {
  id: number
  title: string
  content: string
  /** 后端为字符串枚举，非数字 */
  msgType: MessageType | string
  isRead: number
  bizType?: string | null
  bizId?: number | null
  readTime?: string | null
  createdAt: string
}

export interface UnreadStats {
  total: number
  TRADE: number
  AUDIT: number
  SETTLE: number
  SYSTEM: number
}

export const messageApi = {
  list(params: { pageNum?: number; pageSize?: number; msgType?: string }) {
    return request<PageResult<UserMessage>>({ url: '/v1/messages', method: 'get', params })
  },
  unreadCount() {
    return request<{ count: number }>({ url: '/v1/messages/unread-count', method: 'get' })
  },
  unreadStats() {
    return request<UnreadStats>({ url: '/v1/messages/unread-stats', method: 'get' })
  },
  /** 后端按 ids 数组处理，单条也走同一个接口 */
  read(id: number) {
    return request<void>({ url: '/v1/messages/read', method: 'post', data: { ids: [id] } })
  },
  readAll() {
    return request<void>({ url: '/v1/messages/read-all', method: 'post' })
  },
}
