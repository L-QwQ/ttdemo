import { ref } from 'vue'
import { defineStore } from 'pinia'
import { noticeApi } from '@/api/notice'
import { tokenStorage } from '@/utils/storage'

/**
 * 全局未读通知数，用于顶栏红点。
 * 主动调用 refresh() 拉取，不轮询。
 */
export const useNoticeStore = defineStore('notice', () => {
  const unread = ref(0)

  async function refresh() {
    if (!tokenStorage.getAccessToken()) {
      unread.value = 0
      return
    }
    try {
      unread.value = await noticeApi.unreadCount()
    } catch {
      // 未登录或接口异常时静默失败，不影响页面
    }
  }

  function reset() {
    unread.value = 0
  }

  return { unread, refresh, reset }
})
