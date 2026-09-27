<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Flag, Settings } from '@lucide/vue'

const route = useRoute()
const isGoalsCanvas = computed(() => route.name === 'goals')
const companionAssetPath = '/illustrations/hikari-pixel.svg'

const navigation = [{ to: '/goals', label: '目标', icon: Flag }]

const currentPath = computed(() => route.path)
const isActive = (path: string) =>
  currentPath.value === path || currentPath.value.startsWith(`${path}/`)
const currentLabel = computed(() => navigation.find((item) => isActive(item.to))?.label ?? '设置')
</script>

<template>
  <div class="app-shell" :class="{ 'is-goals-canvas': isGoalsCanvas }">
    <aside v-if="!isGoalsCanvas" class="desktop-rail" aria-label="应用侧栏">
      <RouterLink class="rail-brand" to="/goals" aria-label="Goaltree 目标">
        <span class="brand-pixel" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <strong>GT</strong>
      </RouterLink>
      <nav class="rail-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          class="rail-link"
          :class="{ 'is-active': isActive(item.to) }"
          :to="item.to"
          :aria-current="isActive(item.to) ? 'page' : undefined"
        >
          <span class="rail-icon"><component :is="item.icon" :size="22" aria-hidden="true" /></span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <RouterLink
        class="rail-link rail-settings"
        :class="{ 'is-active': isActive('/settings') }"
        to="/settings"
        aria-label="设置"
      >
        <span class="rail-icon"><Settings :size="22" aria-hidden="true" /></span>
        <span>设置</span>
      </RouterLink>
    </aside>

    <header v-if="!isGoalsCanvas" class="app-topbar">
      <RouterLink class="app-brand" to="/goals">
        <span class="brand-pixel small" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <span><strong>Goaltree</strong><small>我的目标记录</small></span>
      </RouterLink>
      <span class="screen-name" aria-live="polite">{{ currentLabel }}</span>
      <RouterLink
        class="topbar-avatar"
        to="/settings"
        aria-label="打开个人设置"
        :aria-current="isActive('/settings') ? 'page' : undefined"
      >
        <img :src="companionAssetPath" alt="" width="40" height="40" aria-hidden="true" />
      </RouterLink>
    </header>

    <main id="main-content" class="main-content" tabindex="-1"><slot /></main>

    <nav v-if="!isGoalsCanvas" class="mobile-tabbar" aria-label="移动端主导航">
      <RouterLink
        v-for="item in navigation"
        :key="item.to"
        class="tabbar-link"
        :class="{ 'is-active': isActive(item.to) }"
        :to="item.to"
        :aria-current="isActive(item.to) ? 'page' : undefined"
      >
        <span class="tabbar-icon"><component :is="item.icon" :size="22" aria-hidden="true" /></span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
