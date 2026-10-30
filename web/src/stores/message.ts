import { ref } from 'vue'
import { defineStore } from 'pinia'
import { messageApi } from '@/api/message'
import { tokenStorage } from '@/utils/storage'

/**
 * 业务消息未读数（审核/结算/交易/系统），顶栏红点用。
 * 与 noticeStore（平台公告）分开，避免两个概念混用。
 */
export const useMessageStore = defineStore('message', () => {
  const unread = ref(0)

  async function refresh() {
    if (!tokenStorage.getAccessToken()) {
      unread.value = 0
      return
    }
    try {
      const stats = await messageApi.unreadStats()
      unread.value = stats.total
    } catch {
      // 未登录或接口异常静默失败
    }
  }

  function reset() {
    unread.value = 0
  }

  return { unread, refresh, reset }
})
