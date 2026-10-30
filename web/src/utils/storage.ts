import type { TokenResponse, UserVO } from '@/types'

const ACCESS_TOKEN_KEY = 'ttdemo_access_token'
const REFRESH_TOKEN_KEY = 'ttdemo_refresh_token'
const USER_KEY = 'ttdemo_user'

export const tokenStorage = {
  getAccessToken(): string {
    return localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
  },
  getRefreshToken(): string {
    return localStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
  },
  getUser(): UserVO | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserVO
    } catch {
      return null
    }
  },
  saveSession(payload: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
    if (payload.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
    }
  },
  saveUser(user: UserVO): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}
