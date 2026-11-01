<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found ui-page view-not-found">
    <p class="code">404</p>
    <p class="desc">你访问的页面不存在或已被移除</p>
    <el-button type="primary" @click="router.push('/')">回到首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 70vh;
  text-align: center;
}

.code {
  margin: 0;
  font-size: 72px;
  font-weight: 700;
  color: var(--brand);
  letter-spacing: -0.03em;
}

.desc {
  margin: 0 0 12px;
  color: var(--text-sub);
}
</style>
