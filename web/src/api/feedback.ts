import { request } from './http'
import type { Feedback, FeedbackType, PageResult } from '@/types'

export const feedbackApi = {
  create(payload: { type: FeedbackType; title: string; content: string; contact?: string }) {
    return request<number>({ url: '/feedback', method: 'post', data: payload })
  },
  my(query: { page?: number; size?: number }) {
    return request<PageResult<Feedback>>({ url: '/feedback/my', method: 'get', params: query })
  },
}
