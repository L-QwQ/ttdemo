import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const EP_COMPONENTS_DIR = fileURLToPath(
  new URL('./node_modules/element-plus/es/components', import.meta.url),
)

/**
 * 建立「组件名 -> 逐组件入口文件」映射。
 *
 * 官方 ElementPlusResolver 把一切解析到 `element-plus/es`（即 es/index.mjs），
 * 那是一个导出全部组件的巨型 barrel：预构建时无法 tree-shake，会打成约 2MB 单文件
 * 并附带 4.2MB sourcemap，DevTools 打开时必须完整解析，是 F12 卡顿的直接原因。
 *
 * 注意 ElFormItem / ElTableColumn / ElAside / ElOption 这类子组件没有独立入口目录，
 * 只在父组件入口（form / table / container / select）里被导出，因此这里按
 * “实际导出它的目录”定位，而不是简单转 kebab-case。
 */
function buildElementPlusComponentPaths(): Map<string, string> {
  const map = new Map<string, string>()
  if (!fs.existsSync(EP_COMPONENTS_DIR)) {
    console.warn(`[vite] 未找到 element-plus 组件目录：${EP_COMPONENTS_DIR}`)
    return map
  }
  for (const dir of fs.readdirSync(EP_COMPONENTS_DIR)) {
    const entry = path.join(EP_COMPONENTS_DIR, dir, 'index.mjs')
    if (!fs.existsSync(entry)) continue
    const code = fs.readFileSync(entry, 'utf8')
    for (const stmt of code.match(/export\s*\{[^}]*\}/g) ?? []) {
      for (const name of stmt.match(/\bEl[A-Z][A-Za-z0-9]*\b/g) ?? []) {
        if (!map.has(name)) {
          map.set(name, `element-plus/es/components/${dir}/index.mjs`)
        }
      }
    }
  }
  return map
}

const EP_COMPONENT_PATHS = buildElementPlusComponentPaths()

type ResolverEntry = {
  type: 'component' | 'directive'
  resolve: (name: string) => unknown
}

type ResolveResult = { name: string; from: string; sideEffects?: unknown }

/**
 * 包装官方 resolver：保留它全部的组件名映射与样式注入逻辑，仅把 JS 入口
 * 从 barrel 改写为逐组件子路径；若某个名字查不到（如未来版本改名）则原样回退，
 * 保证不会因优化而解析失败。
 */
function elementPlusOnDemandResolver() {
  return (ElementPlusResolver() as unknown as ResolverEntry[]).map((entry) => ({
    ...entry,
    resolve: async (name: string) => {
      const result = (await entry.resolve(name)) as ResolveResult | string | null | undefined
      if (!result || typeof result !== 'object' || result.from !== 'element-plus/es') {
        return result
      }
      // 指令的入参名（Loading）与导出名（ElLoadingDirective）不同，两个都试
      const subpath = EP_COMPONENT_PATHS.get(result.name) ?? EP_COMPONENT_PATHS.get(name)
      return subpath ? { ...result, from: subpath } : result
    },
  })) as unknown as ReturnType<typeof ElementPlusResolver>
}

const epResolver = elementPlusOnDemandResolver()

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板组件与 ElMessage/ElMessageBox/v-loading 全部按需解析并注入对应样式，
    // 替代原先的全量 app.use(ElementPlus) + dist/index.css（2MB 预构建产物 + 4.2MB sourcemap）。
    AutoImport({
      resolvers: [epResolver],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [epResolver],
      // 不生成组件类型声明：一旦声明精确组件类型，el-table 插槽的 scope.row 会由 any 收紧为
      // DefaultRow，历史模板里大量 scope.row 直传业务类型的写法会报 27 处类型错误。
      // 关闭后与全量 app.use(ElementPlus) 时的类型行为一致，按需打包收益不受影响。
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境直接代理到后端，前端始终用相对路径 /api
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
