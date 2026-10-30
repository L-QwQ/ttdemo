<script setup lang="ts">
/**
 * 开奖号码球组件：按彩种渲染红球/蓝球/数字球
 * SSQ: red 6 + blue 1；DLT: red 5(前区) + blue 2(后区)
 * FC3D/PL3: 3 位数字单色球；KL8: 最多 10 个号码球
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    typeCode: string
    red?: string | null
    blue?: string | null
    size?: 'sm' | 'md' | 'lg'
    /** 最多展示几个红球（0 = 全部），超出显示 +N */
    max?: number
  }>(),
  { red: '', blue: '', size: 'md', max: 0 },
)

const redAll = computed(() => splitNumbers(props.red))
const redList = computed(() => (props.max > 0 ? redAll.value.slice(0, props.max) : redAll.value))
const hiddenCount = computed(() => Math.max(0, redAll.value.length - redList.value.length))
const blueList = computed(() => splitNumbers(props.blue))

function splitNumbers(raw: string | null | undefined): string[] {
  if (!raw) return []
  return raw
    .split(/[,;\s，；]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

const isBallType = computed(() => props.typeCode === 'SSQ' || props.typeCode === 'DLT')
const isDigitType = computed(() => props.typeCode === 'FC3D' || props.typeCode === 'PL3')

const sizePx = computed(() => ({ sm: 26, md: 32, lg: 38 })[props.size])
const fontSize = computed(() => ({ sm: 12, md: 14, lg: 16 })[props.size])
</script>

<template>
  <div v-if="redList.length || blueList.length" class="ball-group">
    <!-- 双色球 / 大乐透 -->
    <template v-if="isBallType">
      <span
        v-for="(n, i) in redList"
        :key="`r${i}`"
        class="ball red"
        :style="{ width: sizePx + 'px', height: sizePx + 'px', fontSize: fontSize + 'px' }"
      >{{ n }}</span>
      <span
        v-for="(n, i) in blueList"
        :key="`b${i}`"
        class="ball blue"
        :style="{ width: sizePx + 'px', height: sizePx + 'px', fontSize: fontSize + 'px' }"
      >{{ n }}</span>
    </template>

    <!-- 福彩3D / 排列3：数字球 -->
    <template v-else-if="isDigitType">
      <span
        v-for="(n, i) in redList"
        :key="`d${i}`"
        class="ball digit"
        :style="{ width: sizePx + 'px', height: sizePx + 'px', fontSize: fontSize + 'px' }"
      >{{ n }}</span>
    </template>

    <!-- 快乐8：橙金球 -->
    <template v-else>
      <span
        v-for="(n, i) in redList"
        :key="`k${i}`"
        class="ball gold"
        :style="{ width: sizePx + 'px', height: sizePx + 'px', fontSize: fontSize + 'px' }"
      >{{ n }}</span>
      <span v-if="hiddenCount > 0" class="more-hint">+{{ hiddenCount }}</span>
    </template>
  </div>
  <span v-else class="no-draw">暂未开奖</span>
</template>

<style scoped>
.ball-group {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.ball {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  font-family: 'DIN Alternate', 'Helvetica Neue', Consolas, monospace;
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.18), 0 2px 4px rgba(0, 0, 0, 0.12);
  user-select: none;
}

.ball.red {
  background: radial-gradient(circle at 30% 28%, #f2705c 0%, #d92b2b 60%, #a81f1f 100%);
}

.ball.blue {
  background: radial-gradient(circle at 30% 28%, #5c95f2 0%, #2b6cd9 60%, #1d4fa8 100%);
}

.ball.digit {
  background: radial-gradient(circle at 30% 28%, #f2a15c 0%, #d9622b 60%, #a8471d 100%);
}

.ball.gold {
  background: radial-gradient(circle at 30% 28%, #f2c95c 0%, #d9a02b 60%, #a8751d 100%);
}

.no-draw {
  font-size: 13px;
  color: var(--text-sub);
}

.more-hint {
  font-size: 12px;
  color: var(--text-sub);
  padding: 0 4px;
}
</style>
