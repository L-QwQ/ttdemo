import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 按需引入后组件样式由 unplugin 自动注入，但 --el-* 全局 CSS 变量只定义在 base.css 里，
// 组件样式文件仅 var() 引用，因此 base.css 必须保留，否则全站配色与字号全部失效。
import 'element-plus/theme-chalk/base.css'

import './styles/main.css'
import './styles/pages.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
