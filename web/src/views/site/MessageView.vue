<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { messageApi, type MessageType, type UnreadStats, type UserMessage } from '@/api/message'
import { useMessageStore } from '@/stores/message'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const messageStore = useMessageStore()

const TABS: { key: '' | MessageType; label: string; hint: string }[] = [
  { key: '', label: '全部', hint: '所有业务通知' },
  { key: 'TRADE', label: '交易', hint: '解锁退款、权益兑换发放' },
  { key: 'AUDIT', label: '审核', hint: '预测过审、驳回、下架' },
  { key: 'SETTLE', label: '结算', hint: '期次收益入账' },
  { key: 'SYSTEM', label: '系统', hint: '举报处理、账号相关' },
]

const TYPE_META: Record<string, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  TRADE: { label: '交易', type: 'success' },
  AUDIT: { label: '审核', type: 'warning' },
  SETTLE: { label: '结算', type: 'danger' },
  SYSTEM: { label: '系统', type: 'info' },
}

const list = ref<UserMessage[]>([])
const total = ref(0)
const page = ref(1)
const activeTab = ref<'' | MessageType>('')
const stats = ref<UnreadStats>({ total: 0, TRADE: 0, AUDIT: 0, SETTLE: 0, SYSTEM: 0 })
const loading = ref(false)
const detail = ref<UserMessage | null>(null)
const dialogVisible = ref(false)

const pageSize = 10
const activeHint = computed(() => TABS.find((t) => t.key === activeTab.value)?.hint ?? '')
const isEmpty = computed(() => !loading.value && list.value.length === 0)

function statOf(key: '' | MessageType): number {
  if (!key) return stats.value.total
  return stats.value[key] ?? 0
}

/** 业务消息落到具体页面，避免用户读完还得自己找 */
function jumpTarget(row: UserMessage): { path: string; label: string } | null {
  const bizId = row.bizId
  switch (row.bizType) {
    case 'PREDICTION':
      return bizId ? { path: `/prediction/${bizId}`, label: '查看预测' } : null
    case 'SETTLEMENT':
      return { path: '/creator/earnings', label: '查看收益明细' }
    case 'ORDER':
      return { path: '/my/purchased', label: '查看已购内容' }
    case 'REWARD':
      return { path: '/rewards', label: '查看兑换记录' }
    case 'REPORT':
    case 'DEACTIVATION':
      return { path: '/profile', label: '前往个人中心' }
    default:
      return null
  }
}

const detailTarget = computed(() => (detail.value ? jumpTarget(detail.value) : null))

async function loadStats() {
  try {
    stats.value = await messageApi.unreadStats()
    syncTopbar()
  } catch {
    // 角标失败不影响列表
  }
}

async function load() {
  loading.value = true
  try {
    const result = await messageApi.list({
      pageNum: page.value,
      pageSize,
      msgType: activeTab.value || undefined,
    })
    list.value = result.list
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function reload() {
  await Promise.all([load(), loadStats()])
}

function switchTab(key: '' | MessageType) {
  if (activeTab.value === key) return
  activeTab.value = key
  page.value = 1
}

async function open(row: UserMessage) {
  detail.value = row
  dialogVisible.value = true
  if (row.isRead === 0) {
    try {
      await messageApi.read(row.id)
      row.isRead = 1
      const statKey = row.msgType as MessageType
      if (stats.value[statKey] > 0) stats.value[statKey] -= 1
      stats.value.total = Math.max(0, stats.value.total - 1)
    } catch {
      // 标记失败不阻塞阅读
    }
    syncTopbar()
  }
}

/** 同步顶栏角标 */
function syncTopbar() {
  messageStore.unread = stats.value.total
}

async function goBiz() {
  const target = detailTarget.value
  if (!target) return
  dialogVisible.value = false
  await router.push(target.path)
}

async function readAll() {
  await messageApi.readAll()
  ElMessage.success('已全部标记为已读')
  await reload()
}

watch(activeTab, load)
onMounted(reload)
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>消息中心</h1>
        <p class="sub">
          你的业务进度通知：审核结论、收益入账、退款与兑换。平台公告请到
          <router-link to="/notices" class="link">通知中心</router-link>。
        </p>
      </div>
      <el-button :disabled="stats.total === 0" @click="readAll">全部已读</el-button>
    </header>

    <div class="tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
        <span v-if="statOf(tab.key) > 0" class="tab-badge">
          {{ statOf(tab.key) > 99 ? '99+' : statOf(tab.key) }}
        </span>
      </button>
    </div>

    <p class="hint">{{ activeHint }} · 共 {{ total }} 条</p>

    <div v-loading="loading" class="list">
      <div v-if="isEmpty" class="empty">
        <p class="empty-title">{{ activeTab ? '该分类暂无消息' : '暂无消息' }}</p>
        <p class="empty-sub">
          审核结论、结算入账、退款到账等业务变动会在这里通知你
        </p>
      </div>

      <div
        v-for="row in list"
        :key="row.id"
        class="msg"
        :class="{ unread: row.isRead === 0 }"
        @click="open(row)"
      >
        <div class="msg-head">
          <div class="msg-tags">
            <el-tag size="small" :type="TYPE_META[row.msgType]?.type ?? 'info'" effect="plain">
              {{ TYPE_META[row.msgType]?.label ?? '系统' }}
            </el-tag>
            <span v-if="row.isRead === 0" class="dot" />
          </div>
          <span class="time">{{ formatDateTime(row.createdAt) }}</span>
        </div>
        <p class="title">{{ row.title }}</p>
        <p class="content">{{ row.content }}</p>
      </div>
    </div>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="detail?.title" width="540px">
      <div class="dialog-meta">
        <el-tag size="small" :type="TYPE_META[detail?.msgType ?? '']?.type ?? 'info'" effect="plain">
          {{ TYPE_META[detail?.msgType ?? '']?.label ?? '系统' }}
        </el-tag>
        <span class="dialog-time">{{ formatDateTime(detail?.createdAt) }}</span>
      </div>
      <p class="dialog-content">{{ detail?.content }}</p>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="detailTarget" type="primary" @click="goBiz">{{ detailTarget.label }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { max-width: 860px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 6px; }
h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; line-height: 1.7; color: var(--text-sub); }
.link { color: var(--brand); text-decoration: none; }
.link:hover { text-decoration: underline; }

.tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 10px; }
.tab {
  display: inline-flex; align-items: center; gap: 5px;
  background: #fff; border: 1px solid var(--border-soft); border-radius: 18px;
  padding: 5px 14px; font-size: 13px; color: var(--text-main); cursor: pointer;
  transition: all .18s;
}
.tab:hover { border-color: var(--brand); color: var(--brand); }
.tab.active { background: var(--brand); border-color: var(--brand); color: #fff; }
.tab-badge { min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: var(--brand); color: #fff; font-size: 10px; line-height: 16px; text-align: center; }
.tab.active .tab-badge { background: rgba(255, 255, 255, .28); }
.hint { margin: 0 0 12px; font-size: 12px; color: var(--text-sub); }

.list { min-height: 180px; }
.empty { padding: 48px 0; text-align: center; }
.empty-title { margin: 0 0 6px; font-size: 14px; color: var(--text-main); }
.empty-sub { margin: 0; font-size: 13px; color: var(--text-sub); }

.msg { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px; margin-bottom: 10px; cursor: pointer; transition: box-shadow .18s, border-color .18s; }
.msg:hover { box-shadow: 0 4px 14px rgba(0, 0, 0, .06); }
.msg.unread { border-left: 3px solid var(--brand); }
.msg-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 8px; }
.msg-tags { display: flex; align-items: center; gap: 8px; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: var(--brand); }
.time { font-size: 12px; color: var(--text-sub); white-space: nowrap; }
.title { margin: 0 0 6px; font-size: 14px; font-weight: 600; color: var(--text-main); }
.content { margin: 0; font-size: 13px; line-height: 1.7; color: var(--text-sub); overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; white-space: pre-wrap; }

.pager { justify-content: flex-end; margin-top: 14px; }
.dialog-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.dialog-time { font-size: 12px; color: var(--text-sub); }
.dialog-content { margin: 0; font-size: 14px; line-height: 1.9; white-space: pre-wrap; color: var(--text-main); }
</style>
