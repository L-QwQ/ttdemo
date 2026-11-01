<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { noticeApi } from '@/api/notice'
import { useNoticeStore } from '@/stores/notice'
import { formatDateTime } from '@/utils/format'
import type { MyNotice, NoticeLevel, NoticeType, UserNoticeDetail } from '@/types'

const noticeStore = useNoticeStore()

const loading = ref(false)
const rows = ref<MyNotice[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  unreadOnly: false,
  type: '' as NoticeType | '',
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<UserNoticeDetail | null>(null)

const LEVEL_META: Record<NoticeLevel, { label: string; type: 'info' | 'warning' | 'danger' }> = {
  NORMAL: { label: '普通', type: 'info' },
  IMPORTANT: { label: '重要', type: 'warning' },
  URGENT: { label: '紧急', type: 'danger' },
}

const hasRows = computed(() => rows.value.length > 0)

async function fetchList() {
  loading.value = true
  try {
    const result = await noticeApi.list({
      page: query.page,
      size: query.size,
      unreadOnly: query.unreadOnly || undefined,
      type: query.type || undefined,
    })
    rows.value = result.list
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  fetchList()
  noticeStore.refresh()
}

async function openNotice(row: MyNotice) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await noticeApi.detail(row.id)
    if (!row.readAt) {
      row.readAt = detail.value.readAt
      noticeStore.refresh()
    }
  } catch {
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function markAllRead() {
  try {
    const count = await noticeApi.markAllRead()
    ElMessage.success(count > 0 ? `已把 ${count} 条通知标记为已读` : '没有未读通知')
    reload()
  } catch {
    // 拦截器已提示
  }
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="notice-page ui-page view-notice">
    <div class="page-card">
      <div class="page-head">
        <div>
          <h2 class="page-title">通知中心</h2>
          <p class="page-desc">
            平台发布的公告与定向通知<span v-if="noticeStore.unread > 0">，当前 {{ noticeStore.unread }} 条未读</span>。
            审核结论、收益入账、退款等业务进度请查看
            <router-link to="/messages" class="inline-link">消息中心</router-link>。
          </p>
        </div>
        <div class="head-actions">
          <el-radio-group v-model="query.type" @change="reload">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="ANNOUNCEMENT">公告</el-radio-button>
            <el-radio-button value="NOTICE">通知</el-radio-button>
          </el-radio-group>
          <el-switch
            v-model="query.unreadOnly"
            active-text="只看未读"
            @change="reload"
          />
          <el-button :disabled="noticeStore.unread === 0" @click="markAllRead">全部已读</el-button>
        </div>
      </div>

      <div v-loading="loading" class="notice-list">
        <template v-if="hasRows">
          <article
            v-for="item in rows"
            :key="item.id"
            class="notice-item"
            :class="{ unread: !item.readAt }"
            tabindex="0" role="button" @keydown.enter="openNotice(item)" @keydown.space.prevent="openNotice(item)" @click="openNotice(item)"
          >
            <span class="dot" :class="{ active: !item.readAt }" />
            <div class="item-main">
              <h3>
                <span class="item-title">{{ item.title }}</span>
                <el-tag size="small" effect="plain">{{ item.type === 'ANNOUNCEMENT' ? '公告' : '通知' }}</el-tag>
                <el-tag
                  v-if="item.level !== 'NORMAL'"
                  size="small"
                  :type="LEVEL_META[item.level].type"
                  effect="light"
                >
                  {{ LEVEL_META[item.level].label }}
                </el-tag>
              </h3>
              <p class="item-meta">
                发布人：{{ item.publisherName || '系统' }} · {{ formatDateTime(item.publishedAt) }}
              </p>
            </div>
            <span class="item-time">{{ item.readAt ? '已读' : '未读' }}</span>
          </article>
        </template>
        <el-empty v-else-if="!loading" description="暂无通知" />
      </div>

      <div v-if="total > query.size" class="pager">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" size="520px" :title="detail?.title ?? '通知详情'">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <div class="drawer-meta">
            <el-tag size="small" effect="plain">
              {{ detail.type === 'ANNOUNCEMENT' ? '公告' : '通知' }}
            </el-tag>
            <el-tag v-if="detail.level !== 'NORMAL'" size="small" :type="LEVEL_META[detail.level].type">
              {{ LEVEL_META[detail.level].label }}
            </el-tag>
            <span>{{ detail.publisherName || '系统' }}</span>
            <span>{{ formatDateTime(detail.publishedAt) }}</span>
          </div>
          <div class="drawer-content">{{ detail.content }}</div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.inline-link {
  color: var(--brand);
  text-decoration: none;
}

.inline-link:hover {
  text-decoration: underline;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.notice-list {
  min-height: 120px;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--border-soft);
  cursor: pointer;
  transition: background 0.15s;
}

.notice-item:hover {
  background: var(--surface-hover);
}

.notice-item.unread .item-title {
  font-weight: 600;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dfe3eb;
  flex: none;
}

.dot.active {
  background: var(--brand);
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-main h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 500;
}

.item-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 460px;
}

.item-meta {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
}

.item-time {
  font-size: 12px;
  color: var(--text-sub);
  flex: none;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-body {
  min-height: 120px;
}

.drawer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-sub);
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-soft);
}

.drawer-content {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.85;
  color: var(--text-main);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
