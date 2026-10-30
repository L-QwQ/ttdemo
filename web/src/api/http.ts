import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResult, TokenResponse } from '@/types'
import { tokenStorage } from '@/utils/storage'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) return null
  try {
    // 用裸 axios，避免刷新请求再次进入拦截器造成死循环
    const { data } = await axios.post<ApiResult<TokenResponse>>(`${BASE_URL}/auth/refresh`, { refreshToken })
    if (data?.code === 0 && data.data?.accessToken) {
      tokenStorage.saveSession(data.data)
      return data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

/** 令牌失效统一走前台登录页，带上回跳地址（登录后按角色自动落地） */
function redirectToLogin(): void {
  const path = window.location.pathname
  if (path.startsWith('/login') || path.startsWith('/admin/login')) return
  const from = path + window.location.search + window.location.hash
  window.location.href = `/login?redirect=${encodeURIComponent(from)}`
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    const config = error.config as RetriableConfig | undefined
    const status = error.response?.status

    // accessToken 过期：静默刷新一次后重放原请求
    if (status === 401 && config && !config._retry) {
      config._retry = true
      refreshPromise = refreshPromise ?? refreshAccessToken().finally(() => { refreshPromise = null })
      const newToken = await refreshPromise
      if (newToken) {
        config.headers.set('Authorization', `Bearer ${newToken}`)
        return http.request(config)
      }
      tokenStorage.clear()
      ElMessage.error('登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(error)
    }

    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/** 统一解包：返回业务 data，失败时抛出异常（已由拦截器提示） */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}

export default http
