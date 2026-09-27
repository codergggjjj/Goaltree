<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { RotateCcw, Trash2, X } from '@lucide/vue'
import type { ApiProblem, TrashGoalItem } from './types'

const props = defineProps<{
  open: boolean
  items: TrashGoalItem[]
  loading: boolean
  saving: boolean
  error: ApiProblem | null
  actionError?: string
}>()
const emit = defineEmits<{ close: [], retry: [], restore: [item: TrashGoalItem] }>()
const panel = ref<HTMLElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)

watch(() => props.open, (open) => {
  if (open) void nextTick(() => closeButton.value?.focus())
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !panel.value) return
  const buttons = [...panel.value.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')]
  if (!buttons.length) return
  if (event.shiftKey && document.activeElement === buttons[0]) {
    event.preventDefault()
    buttons[buttons.length - 1]?.focus()
  } else if (!event.shiftKey && document.activeElement === buttons[buttons.length - 1]) {
    event.preventDefault()
    buttons[0]?.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="next-action-overlay" @mousedown.self="emit('close')">
      <section ref="panel" class="next-action-drawer goal-trash-drawer" role="dialog" aria-modal="true" aria-labelledby="goal-trash-title" @keydown="onKeydown">
        <header class="next-action-header">
          <div>
            <span class="next-action-eyebrow"><Trash2 :size="16" aria-hidden="true" />已删除目标</span>
            <h2 id="goal-trash-title">回收站</h2>
            <p>可恢复单个目标，或一次删除的整棵目标分支。</p>
          </div>
          <button ref="closeButton" type="button" class="next-action-close" aria-label="关闭回收站" @click="emit('close')"><X :size="20" aria-hidden="true" /></button>
        </header>
        <div class="next-action-summary">{{ items.length }} 项可查看的删除记录</div>
        <p v-if="actionError" class="goal-trash-error" role="alert">{{ actionError }}</p>
        <div v-if="loading" class="next-action-empty" role="status">正在加载回收站…</div>
        <div v-else-if="error" class="next-action-empty" role="alert">
          <strong>回收站暂时无法加载</strong>
          <p>{{ error.message }}</p>
          <button class="secondary-button" type="button" @click="emit('retry')">重试</button>
        </div>
        <ul v-else-if="items.length" class="goal-trash-list" aria-label="已删除目标">
          <li v-for="item in items" :key="item.id" class="goal-trash-row">
            <div class="goal-trash-row-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.descendantCount ? `包含 ${item.descendantCount} 个子目标` : '单个目标' }} · {{ new Date(item.deletedAt).toLocaleString('zh-CN') }}</small>
              <small v-if="!item.canRestore" class="goal-trash-hint">请先恢复它的父目标</small>
            </div>
            <button type="button" class="secondary-button" :disabled="saving || !item.canRestore" :aria-label="`恢复${item.title}`" @click="emit('restore', item)"><RotateCcw :size="15" aria-hidden="true" />恢复</button>
          </li>
        </ul>
        <div v-else class="next-action-empty" role="status">
          <Trash2 :size="25" aria-hidden="true" />
          <strong>回收站是空的</strong>
          <p>删除的目标会显示在这里，方便恢复。</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>
