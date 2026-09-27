<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ArrowUpRight, Circle, ListTodo, X } from '@lucide/vue'
import type { GoalListItem } from './types'
import type { NextAction } from './goalTree'

const props = defineProps<{ open: boolean, root: GoalListItem | null, actions: NextAction[] }>()
const emit = defineEmits<{ close: [], locate: [id: string] }>()
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
  const first = buttons[0]!
  const last = buttons[buttons.length - 1]!
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="next-action-overlay" @mousedown.self="emit('close')">
      <section ref="panel" class="next-action-drawer" role="dialog" aria-modal="true" aria-labelledby="next-action-title" @keydown="onKeydown">
        <header class="next-action-header">
          <div>
            <span class="next-action-eyebrow"><ListTodo :size="16" aria-hidden="true" />当前主目标</span>
            <h2 id="next-action-title">下一步做什么</h2>
            <p v-if="root">{{ root.title }}</p>
          </div>
          <button ref="closeButton" type="button" class="next-action-close" aria-label="关闭下一步抽屉" @click="emit('close')"><X :size="20" aria-hidden="true" /></button>
        </header>
        <div class="next-action-summary">{{ actions.length }} 项未完成的末级目标</div>
        <ul v-if="actions.length" class="next-action-list" aria-label="下一步目标">
          <li v-for="action in actions" :key="action.goal.id">
            <button type="button" :aria-label="`定位${action.goal.title}`" @click="emit('locate', action.goal.id)">
              <Circle :size="17" aria-hidden="true" />
              <span class="next-action-copy">
                <strong>{{ action.goal.title }}</strong>
                <small v-if="action.path.length">{{ action.path.join(' › ') }}</small>
                <small v-else-if="action.goal.status === 'PAUSED'">已暂停</small>
              </span>
              <ArrowUpRight :size="17" aria-hidden="true" />
            </button>
          </li>
        </ul>
        <div v-else class="next-action-empty" role="status">
          <Circle :size="24" aria-hidden="true" />
          <strong>{{ root?.status === 'ARCHIVED' ? '这棵目标树已归档' : '当前没有待完成的末级目标' }}</strong>
          <p>{{ root?.status === 'ARCHIVED' ? '恢复主目标后，可继续从这里定位下一步。' : '新建子目标，或继续规划另一棵目标树。' }}</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>
