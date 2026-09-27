<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Check, Pencil, Plus, Trash2, Undo2, X } from '@lucide/vue'
import GoalLinks from './GoalLinks.vue'
import type { GoalListItem } from './types'

const props = defineProps<{ goal: GoalListItem | null, canMoveUp: boolean, canMoveDown: boolean, moving: boolean, orderError?: string | null }>()
const emit = defineEmits<{ close: [], move: [direction: 'UP' | 'DOWN'], add: [], edit: [], toggle: [], remove: [] }>()
const closeButton = ref<HTMLButtonElement | null>(null)
watch(() => props.goal?.id, (id) => { if (id) void nextTick(() => closeButton.value?.focus()) })
</script>

<template>
  <Teleport to="body">
    <div v-if="goal" class="sheet-overlay goal-link-overlay" role="presentation" @mousedown.self="emit('close')" @keydown.esc.stop="emit('close')">
      <section class="app-sheet simple-sheet goal-link-dialog" role="dialog" aria-modal="true" :aria-label="`${goal.title}的目标详情`">
        <header class="sheet-header">
          <div><small>目标详情</small><h2>{{ goal.title }}</h2></div>
          <button ref="closeButton" class="icon-button" type="button" aria-label="关闭目标详情" @click="emit('close')"><X :size="22" aria-hidden="true" /></button>
        </header>
        <div class="goal-detail-node-actions" aria-label="目标操作">
          <button type="button" :disabled="moving || goal.status === 'ARCHIVED'" @click="emit('add')"><Plus :size="16" aria-hidden="true" />添加子目标</button>
          <button type="button" :disabled="moving || goal.status === 'ARCHIVED'" @click="emit('edit')"><Pencil :size="16" aria-hidden="true" />编辑</button>
          <button type="button" :disabled="moving" @click="emit('toggle')"><Undo2 v-if="goal.status === 'COMPLETED' || goal.status === 'ARCHIVED'" :size="16" aria-hidden="true" /><Check v-else :size="16" aria-hidden="true" />{{ goal.status === 'COMPLETED' ? '重新打开' : goal.status === 'ARCHIVED' ? '取消归档' : '完成' }}</button>
          <button type="button" class="is-danger" :disabled="moving" @click="emit('remove')"><Trash2 :size="16" aria-hidden="true" />删除</button>
        </div>
        <div class="goal-order-actions" aria-label="同级目标排序">
          <span>同级顺序</span>
          <button type="button" :disabled="!canMoveUp || moving" :aria-label="`上移${goal.title}`" @click="emit('move', 'UP')"><ArrowUp :size="16" aria-hidden="true" />上移</button>
          <button type="button" :disabled="!canMoveDown || moving" :aria-label="`下移${goal.title}`" @click="emit('move', 'DOWN')"><ArrowDown :size="16" aria-hidden="true" />下移</button>
        </div>
        <p v-if="orderError" class="goal-order-error" role="alert">{{ orderError }}</p>
        <GoalLinks :goal-id="goal.id" />
      </section>
    </div>
  </Teleport>
</template>
