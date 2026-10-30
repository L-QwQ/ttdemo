import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { adminApi } from '@/api/admin'
import { userApi } from '@/api/user'
import type { TokenResponse, UserVO } from '@/types'
import { STAFF_ROLES } from '@/types'
import { tokenStorage } from '@/utils/storage'
import { hasPerm } from '@/utils/perms'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(tokenStorage.getAccessToken())
  const user = ref<UserVO | null>(tokenStorage.getUser())

  const isLoggedIn = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  /** 后台人员（管理员 / 运营 / 审核 / 财务），登录时据此决定落地页与后台入口 */
  const isStaff = computed(() => STAFF_ROLES.includes(user.value?.role ?? 'USER'))
  const displayName = computed(() => user.value?.nickname || user.value?.phone || '')
  /** 是否具备某权限点（仅用于前端隐藏，实际拦截在后端） */
  function can(perm: string): boolean {
    return hasPerm(user.value?.role, perm)
  }
  /** 会员是否在有效期内 */
  const isVip = computed(() => {
    const expire = user.value?.vipExpireAt
    return Boolean(expire && new Date(expire).getTime() > Date.now())
  })

  function applySession(payload: TokenResponse) {
    tokenStorage.saveSession(payload)
    accessToken.value = payload.accessToken
    user.value = payload.user
  }

  function clearSession() {
    tokenStorage.clear()
    accessToken.value = ''
    user.value = null
  }

  async function loginBySms(phone: string, code: string) {
    applySession(await authApi.loginBySms({ phone, code }))
  }

  async function loginByPassword(phone: string, password: string) {
    applySession(await authApi.loginByPassword({ phone, password }))
  }

  async function register(payload: { phone: string; code: string; password?: string; nickname?: string; agreed?: boolean; inviteCode?: string }) {
    applySession(await authApi.register(payload))
  }

  async function loginAsAdmin(phone: string, password: string) {
    applySession(await adminApi.login({ phone, password }))
  }

  async function refreshProfile() {
    if (!accessToken.value) return
    const me = await userApi.me()
    user.value = me
    tokenStorage.saveUser(me)
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 退出失败不阻塞本地清理
    }
    clearSession()
  }

  return {
    accessToken,
    user,
    isLoggedIn,
    isAdmin,
    isStaff,
    isVip,
    displayName,
    can,
    applySession,
    clearSession,
    loginBySms,
    loginByPassword,
    register,
    loginAsAdmin,
    refreshProfile,
    logout,
  }
})
