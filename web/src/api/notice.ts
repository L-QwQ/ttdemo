import { request } from './http'
import type { MyNotice, NoticeType, PageResult, UserNoticeDetail } from '@/types'

export const noticeApi = {
  /** 我的通知分页 */
  list(query: { page?: number; size?: number; unreadOnly?: boolean; type?: NoticeType | '' }) {
    return request<PageResult<MyNotice>>({ url: '/notices', method: 'get', params: query })
  },
  unreadCount() {
    return request<number>({ url: '/notices/unread-count', method: 'get' })
  },
  /** 查看详情时服务端自动标记已读 */
  detail(id: number) {
    return request<UserNoticeDetail>({ url: `/notices/${id}`, method: 'get' })
  },
  markAllRead() {
    return request<number>({ url: '/notices/read-all', method: 'post' })
  },
}
