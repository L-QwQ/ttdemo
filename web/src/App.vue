<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <!-- 原 app.use(ElementPlus, { locale }) 的全局中文配置，按需引入后由 ConfigProvider 接管 -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
