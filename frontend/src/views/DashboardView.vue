<script setup lang="ts">
import {
  ArrowRight,
  ChevronRight,
  CircleCheck,
  NotebookPen,
  Route,
  SlidersHorizontal,
  Sparkles,
  Wifi,
  WifiOff,
} from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { useBackendHealth } from '@/services/useBackendHealth'

const { status, version, check } = useBackendHealth()

const today = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
}).format(new Date())

const quickActions = [
  { to: '/goals', label: '记进展', hint: '补充一条事实', icon: NotebookPen, tone: 'violet' },
  { to: '/reviews', label: '做复盘', hint: '回看本周偏差', icon: Route, tone: 'cyan' },
  { to: '/plans', label: '调计划', hint: '只调整未来', icon: SlidersHorizontal, tone: 'amber' },
]
</script>

<template>
  <div class="home-screen">
    <header class="home-header">
      <div>
        <span class="pixel-kicker">{{ today }} · STAGE P0</span>
        <h1>晚上好，冒险者</h1>
        <p>离长期目标又近了一点。先看看主线现在走到哪里。</p>
      </div>
      <RouterLink class="primary-button pixel-button" to="/goals">
        继续主线 <ArrowRight :size="18" aria-hidden="true" />
      </RouterLink>
    </header>

    <section class="quest-card" aria-labelledby="quest-title">
      <div class="quest-copy">
        <div class="quest-meta">
          <span class="pixel-tag"><Sparkles :size="14" aria-hidden="true" /> 本周主线</span>
          <button
            class="sync-button"
            :class="status"
            type="button"
            :aria-label="`后端状态：${status}，点击重新检测`"
            @click="check"
          >
            <Wifi v-if="status === 'online'" :size="14" aria-hidden="true" /><WifiOff
              v-else
              :size="14"
              aria-hidden="true"
            />
            <span v-if="status === 'checking'">同步中</span>
            <span v-else-if="status === 'online'">同步正常 · v{{ version }}</span>
            <span v-else>离线 · 重试</span>
          </button>
        </div>

        <h2 id="quest-title">数据分析能力提升</h2>
        <p class="quest-standard">完成标准：能独立完成一份从清洗到结论的数据分析报告</p>

        <div class="dual-progress">
          <div class="progress-label"><span>实际进度</span><strong>58%</strong></div>
          <div
            class="progress-track actual"
            role="progressbar"
            aria-label="实际进度"
            aria-valuemin="0"
            aria-valuemax="100"
            aria-valuenow="58"
          >
            <i style="width: 58%"></i>
          </div>
          <div class="progress-label plan-label"><span>计划进度</span><strong>64%</strong></div>
          <div
            class="progress-track planned"
            role="progressbar"
            aria-label="计划进度"
            aria-valuemin="0"
            aria-valuemax="100"
            aria-valuenow="64"
          >
            <i style="width: 64%"></i>
          </div>
        </div>

        <RouterLink class="milestone-link" to="/goals">
          <span class="milestone-check"><CircleCheck :size="18" aria-hidden="true" /></span>
          <span><small>下一里程碑</small><strong>完成数据清洗方法总结</strong></span>
          <ChevronRight :size="19" aria-hidden="true" />
        </RouterLink>
      </div>

      <div class="companion-stage">
        <div class="speech-bubble">
          <strong>小光的提示</strong>
          <span>实际比计划慢 6%，先记录原因，不用急着改终点。</span>
        </div>
        <img
          class="pixel-companion"
          src="/illustrations/hikari-pixel.svg"
          alt="原创像素动漫助手小光手持星图终端，为目标旅程提供提示"
          width="400"
          height="440"
        />
      </div>
    </section>

    <section class="quick-section" aria-labelledby="quick-title">
      <div class="section-heading">
        <div>
          <span class="pixel-kicker">ONE TAP</span>
          <h2 id="quick-title">现在要做什么？</h2>
        </div>
        <span>不从待办开始</span>
      </div>
      <div class="quick-grid">
        <RouterLink
          v-for="action in quickActions"
          :key="action.label"
          :to="action.to"
          class="quick-action"
          :class="action.tone"
        >
          <span class="quick-icon"
            ><component :is="action.icon" :size="22" aria-hidden="true"
          /></span>
          <span
            ><strong>{{ action.label }}</strong
            ><small>{{ action.hint }}</small></span
          >
          <ChevronRight :size="18" aria-hidden="true" />
        </RouterLink>
      </div>
    </section>

    <section class="journey-card" aria-labelledby="journey-title">
      <div class="journey-head">
        <div>
          <span class="pixel-kicker">GOAL LOOP</span>
          <h2 id="journey-title">你的目标旅程</h2>
        </div>
        <span class="stage-badge">P0 · 验收中</span>
      </div>
      <ol class="journey-path" aria-label="目标闭环六个阶段">
        <li class="is-current"><b>01</b><span>目标</span></li>
        <li><b>02</b><span>拆解</span></li>
        <li><b>03</b><span>计划</span></li>
        <li><b>04</b><span>进度</span></li>
        <li><b>05</b><span>复盘</span></li>
        <li><b>06</b><span>调整</span></li>
      </ol>
    </section>
  </div>
</template>
