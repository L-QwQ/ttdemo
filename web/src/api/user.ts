import { request } from './http'
import type { UserVO } from '@/types'

export const userApi = {
  me() {
    return request<UserVO>({ url: '/user/me', method: 'get' })
  },
  updateProfile(payload: { nickname?: string; avatar?: string }) {
    return request<UserVO>({ url: '/user/me', method: 'put', data: payload })
  },
  /** 上传头像，返回更新后的用户信息（Content-Type 交给浏览器自动带 boundary） */
  uploadAvatar(file: Blob, filename = 'avatar.jpg') {
    const data = new FormData()
    data.append('file', file, filename)
    return request<UserVO>({ url: '/user/avatar', method: 'post', data, timeout: 30000 })
  },
  changePassword(payload: { oldPassword: string; newPassword: string }) {
    return request<void>({ url: '/user/password', method: 'put', data: payload })
  },
  /** 首次设置密码（无原密码，凭短信验证码） */
  initPassword(payload: { code: string; newPassword: string }) {
    return request<void>({ url: '/user/password/init', method: 'put', data: payload })
  },
}
