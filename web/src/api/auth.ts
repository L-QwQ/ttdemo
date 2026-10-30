import { request } from './http'
import type { SmsScene, SmsSendResult, TokenResponse } from '@/types'

export interface RegisterPayload {
  phone: string
  code: string
  password?: string
  nickname?: string
  /** 是否勾选《用户协议》《积分规则》《免责声明》 */
  agreed?: boolean
  /** 邀请码 */
  inviteCode?: string
}

export const authApi = {
  sendSmsCode(payload: { phone: string; scene: SmsScene }) {
    return request<SmsSendResult>({ url: '/auth/sms-code', method: 'post', data: payload })
  },
  register(payload: RegisterPayload) {
    return request<TokenResponse>({ url: '/auth/register', method: 'post', data: payload })
  },
  loginBySms(payload: { phone: string; code: string }) {
    return request<TokenResponse>({ url: '/auth/login/sms', method: 'post', data: payload })
  },
  loginByPassword(payload: { phone: string; password: string }) {
    return request<TokenResponse>({ url: '/auth/login/password', method: 'post', data: payload })
  },
  resetPassword(payload: { phone: string; code: string; newPassword: string }) {
    return request<void>({ url: '/auth/password/reset', method: 'post', data: payload })
  },
  logout() {
    return request<void>({ url: '/auth/logout', method: 'post' })
  },
}
