<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Maximize2, RotateCcw, Plus, Pencil, Trash2, Check, Undo2, ChevronDown, ChevronRight, Circle } from '@lucide/vue'
import { buildGoalTree, descendantCount, layoutTree, TREE_CARD_WIDTH } from './goalTree'
import type { CanvasView, GoalListItem } from './types'

const collapsedKey = 'goalflow:collapsed-branches:v1'
function readCollapsed(): Record<string, string[]> {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(collapsedKey) ?? '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter(([, ids]) => Array.isArray(ids))
      .map(([id, ids]) => [id, (ids as unknown[]).filter((item): item is string => typeof item === 'string')]))
  } catch { return {} }
}

const props = defineProps<{ goal: GoalListItem | null, goals: GoalListItem[], saving: boolean, savedView?: CanvasView | null, focusTarget?: { id: string, rootId: string, serial: number } | null }>()
const emit = defineEmits<{ add: [id: string], edit: [id: string], remove: [id: string], toggle: [goal: GoalListItem], open: [id: string], viewChange: [id: string, view: CanvasView] }>()
const collapsedByRoot = ref(readCollapsed())
const collapsedIds = computed(() => new Set(collapsedByRoot.value[props.goal?.id ?? ''] ?? []))
const root = computed(() => props.goal ? buildGoalTree(props.goals, props.goal.id) : null)
const layout = computed(() => root.value ? layoutTree(root.value, collapsedIds.value) : null)
const viewport = ref<HTMLElement | null>(null)
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
let observer: ResizeObserver | null = null
let activeGoalId: string | undefined
let drag: { x: number, y: number, tx: number, ty: number } | null = null
const dragging = ref(false)
const locatedId = ref('')
let viewportSize = { width: 0, height: 0 }

function saveView() {
  if (!viewport.value || !props.goal) return
  emit('viewChange', props.goal.id, {
    scale: scale.value,
    centerX: (viewport.value.clientWidth / 2 - tx.value) / scale.value,
    centerY: (viewport.value.clientHeight / 2 - ty.value) / scale.value,
  })
}

function restoreOrFit() {
  if (!viewport.value || !layout.value) return
  activeGoalId = props.goal?.id
  const saved = props.savedView
  if (saved && Number.isFinite(saved.scale) && Number.isFinite(saved.centerX) && Number.isFinite(saved.centerY)) {
    scale.value = Math.max(0.3, Math.min(2.5, saved.scale))
    tx.value = viewport.value.clientWidth / 2 - saved.centerX * scale.value
    ty.value = viewport.value.clientHeight / 2 - saved.centerY * scale.value
  } else fit()
  viewportSize = { width: viewport.value.clientWidth, height: viewport.value.clientHeight }
}

function resizeViewport() {
  if (!viewport.value) return
  if (props.goal?.id !== activeGoalId) return
  const width = viewport.value.clientWidth
  const height = viewport.value.clientHeight
  if (!layout.value || !viewportSize.width || !viewportSize.height) {
    restoreOrFit()
    return
  }
  const centerX = (viewportSize.width / 2 - tx.value) / scale.value
  const centerY = (viewportSize.height / 2 - ty.value) / scale.value
  tx.value = width / 2 - centerX * scale.value
  ty.value = height / 2 - centerY * scale.value
  viewportSize = { width, height }
  saveView()
}

function fit() {
  if (!viewport.value || !layout.value || !root.value) return
  const { clientWidth: width, clientHeight: height } = viewport.value
  const readableScale = width <= 600 ? 0.84 : 0.9
  const fittedScale = Math.min(1.25, (width - 48) / layout.value.width, (height - 48) / layout.value.height)
  scale.value = Math.max(readableScale, fittedScale)
  if (fittedScale < readableScale) {
    anchorRoot()
  } else {
    tx.value = (width - layout.value.width * scale.value) / 2
    ty.value = (height - layout.value.height * scale.value) / 2
  }
  saveView()
}
function reset() {
  scale.value = 1
  anchorRoot()
  saveView()
}
function anchorRoot() {
  if (!viewport.value || !root.value) return
  tx.value = 24 - root.value.x * scale.value
  ty.value = viewport.value.clientHeight / 2 - (root.value.y + root.value.height / 2) * scale.value
}
function zoom(factor: number, x: number, y: number) {
  const next = Math.max(0.3, Math.min(2.5, scale.value * factor))
  tx.value = x - (x - tx.value) * next / scale.value
  ty.value = y - (y - ty.value) * next / scale.value
  scale.value = next
  saveView()
}
function wheel(event: WheelEvent) {
  event.preventDefault()
  if (!viewport.value) return
  const rect = viewport.value.getBoundingClientRect()
  zoom(event.deltaY < 0 ? 1.1 : 1 / 1.1, event.clientX - rect.left, event.clientY - rect.top)
}
function pointerDown(event: PointerEvent) {
  if (event.button !== 0 || (event.target as Element).closest('button, select, a, .tree-card')) return
  drag = { x: event.clientX, y: event.clientY, tx: tx.value, ty: ty.value }
  dragging.value = true
  viewport.value?.setPointerCapture(event.pointerId)
}
function pointerMove(event: PointerEvent) {
  if (!drag) return
  tx.value = drag.tx + event.clientX - drag.x
  ty.value = drag.ty + event.clientY - drag.y
}
function pointerUp() { if (drag) saveView(); drag = null; dragging.value = false }
function cardClick(event: MouseEvent, id: string) {
  if (event.target instanceof Element && event.target.closest('button, a')) return
  emit('open', id)
}
function keydown(event: KeyboardEvent) {
  if (event.target !== viewport.value) return
  if (event.key === 'ArrowLeft') tx.value += 40
  else if (event.key === 'ArrowRight') tx.value -= 40
  else if (event.key === 'ArrowUp') ty.value += 40
  else if (event.key === 'ArrowDown') ty.value -= 40
  else if (event.key === '+' || event.key === '=') zoom(1.1, (viewport.value?.clientWidth ?? 0) / 2, (viewport.value?.clientHeight ?? 0) / 2)
  else if (event.key === '-') zoom(1 / 1.1, (viewport.value?.clientWidth ?? 0) / 2, (viewport.value?.clientHeight ?? 0) / 2)
  else return
  event.preventDefault()
  saveView()
}
async function toggleBranch(id: string) {
  const rootId = props.goal?.id
  const current = layout.value?.nodes.find((node) => node.goal.id === id)
  if (!rootId || !current || !viewport.value || !current.children.length) return
  const screenX = tx.value + (current.x + TREE_CARD_WIDTH / 2) * scale.value
  const screenY = ty.value + (current.y + current.height / 2) * scale.value
  const next = new Set(collapsedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedByRoot.value = { ...collapsedByRoot.value, [rootId]: [...next] }
  try { localStorage.setItem(collapsedKey, JSON.stringify(collapsedByRoot.value)) } catch { /* Storage may be unavailable. */ }
  await nextTick()
  const updated = layout.value?.nodes.find((node) => node.goal.id === id)
  if (!updated) return
  tx.value = screenX - (updated.x + TREE_CARD_WIDTH / 2) * scale.value
  ty.value = screenY - (updated.y + updated.height / 2) * scale.value
  saveView()
}
async function locateGoal(id: string) {
  const rootId = props.goal?.id
  if (!rootId || !viewport.value) return
  const byId = new Map(props.goals.map((goal) => [goal.id, goal]))
  if (!byId.has(id)) return
  const next = new Set(collapsedIds.value)
  const seen = new Set<string>()
  let parentId = byId.get(id)?.parentId
  while (parentId && !seen.has(parentId)) {
    seen.add(parentId)
    next.delete(parentId)
    parentId = byId.get(parentId)?.parentId
  }
  if (next.size !== collapsedIds.value.size) {
    collapsedByRoot.value = { ...collapsedByRoot.value, [rootId]: [...next] }
    try { localStorage.setItem(collapsedKey, JSON.stringify(collapsedByRoot.value)) } catch { /* Storage may be unavailable. */ }
    await nextTick()
  }
  const node = layout.value?.nodes.find((item) => item.goal.id === id)
  if (!node) return
  scale.value = Math.max(scale.value, viewport.value.clientWidth <= 600 ? 0.84 : 0.9)
  tx.value = viewport.value.clientWidth / 2 - (node.x + TREE_CARD_WIDTH / 2) * scale.value
  ty.value = viewport.value.clientHeight / 2 - (node.y + node.height / 2) * scale.value
  locatedId.value = id
  saveView()
  await nextTick()
  const card = [...viewport.value.querySelectorAll<HTMLElement>('.tree-card')].find((item) => item.dataset.goalId === id)
  card?.querySelector<HTMLAnchorElement>('a')?.focus({ preventScroll: true })
}
watch([() => props.goal?.id, () => props.focusTarget?.serial], async ([id], [previousId]) => {
  await nextTick()
  if (id !== previousId) {
    locatedId.value = ''
    restoreOrFit()
  }
  const target = props.focusTarget
  if (target && target.rootId === id) await locateGoal(target.id)
})
onMounted(() => { observer = new ResizeObserver(resizeViewport); if (viewport.value) observer.observe(viewport.value); nextTick(restoreOrFit) })
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section class="goal-tree-canvas" :aria-label="goal ? `${goal.title} 目标树` : '目标树'">
    <div class="tree-toolbar">
      <div class="tree-toolbar-main"><slot name="toolbar" /></div>
      <div class="tree-toolbar-view">
        <button type="button" :disabled="!layout" @click="fit"><Maximize2 :size="16" aria-hidden="true" />适应画布</button>
        <button type="button" :disabled="!layout" @click="reset"><RotateCcw :size="16" aria-hidden="true" />重置视图</button>
      </div>
    </div>
    <slot name="status" />
    <div ref="viewport" class="tree-viewport" :class="{ dragging }" tabindex="0" aria-label="目标画布：在空白处拖动平移，滚轮缩放，方向键平移，加减号缩放" @wheel="wheel" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp" @keydown="keydown">
      <div v-if="layout" class="tree-world" :style="{ width: layout.width + 'px', height: layout.height + 'px', transform: `translate(${tx}px, ${ty}px) scale(${scale})` }">
        <svg class="tree-lines" :width="layout.width" :height="layout.height" aria-hidden="true">
          <path v-for="edge in layout.edges" :key="edge.from.goal.id + edge.to.goal.id" :d="`M ${edge.from.x + TREE_CARD_WIDTH} ${edge.from.y + edge.from.height / 2} C ${edge.from.x + TREE_CARD_WIDTH + 36} ${edge.from.y + edge.from.height / 2}, ${edge.to.x - 36} ${edge.to.y + edge.to.height / 2}, ${edge.to.x} ${edge.to.y + edge.to.height / 2}`" />
        </svg>
        <article v-for="node in layout.nodes" :key="node.goal.id" class="tree-card" :class="{ completed: node.complete, archived: node.goal.status === 'ARCHIVED', 'is-root': node.goal.id === goal?.id, 'is-task': !node.children.length, collapsed: collapsedIds.has(node.goal.id), 'is-located': locatedId === node.goal.id }" :data-goal-id="node.goal.id" :style="{ left: node.x + 'px', top: node.y + 'px', height: node.height + 'px' }" @click="cardClick($event, node.goal.id)">
          <div class="tree-card-main">
            <span class="tree-card-marker" aria-hidden="true"><Check v-if="node.complete" :size="17" /><Circle v-else :size="17" /></span>
            <a :href="`/goals/${node.goal.id}`" :aria-label="`查看${node.goal.title}详情`" @click.prevent.stop="emit('open', node.goal.id)">{{ node.goal.title }}</a>
          </div>
          <span v-if="node.goal.status === 'ARCHIVED' || node.goal.status === 'PAUSED'" class="tree-card-state">{{ node.goal.status === 'ARCHIVED' ? '已归档' : '已暂停' }}</span>
          <button v-if="node.children.length" type="button" class="tree-branch-toggle" :aria-expanded="!collapsedIds.has(node.goal.id)" :aria-label="`${collapsedIds.has(node.goal.id) ? '展开' : '收起'}${node.goal.title}的${descendantCount(node)}个子目标`" @click="toggleBranch(node.goal.id)"><ChevronRight v-if="collapsedIds.has(node.goal.id)" :size="15" aria-hidden="true" /><ChevronDown v-else :size="15" aria-hidden="true" />{{ descendantCount(node) }} 个子目标</button>
          <div class="tree-card-actions" aria-label="目标操作">
            <button type="button" :disabled="saving || node.goal.status === 'ARCHIVED'" :aria-label="`为${node.goal.title}添加子目标`" title="添加子目标" @click="emit('add', node.goal.id)"><Plus :size="17" aria-hidden="true" /></button>
            <button type="button" :disabled="saving || node.goal.status === 'ARCHIVED'" :aria-label="`编辑${node.goal.title}`" title="编辑" @click="emit('edit', node.goal.id)"><Pencil :size="16" aria-hidden="true" /></button>
            <button type="button" :disabled="saving" :aria-label="`${node.goal.status === 'ARCHIVED' ? '取消归档' : node.complete ? '重新打开' : node.goal.status === 'PAUSED' ? '继续' : '完成'}${node.goal.title}`" :title="node.goal.status === 'ARCHIVED' ? '取消归档' : node.complete ? '重新打开' : node.goal.status === 'PAUSED' ? '继续' : '完成'" @click="emit('toggle', node.goal)"><Undo2 v-if="node.complete || node.goal.status === 'ARCHIVED'" :size="17" aria-hidden="true" /><Check v-else :size="17" aria-hidden="true" /></button>
            <button v-if="node.goal.id !== goal?.id" type="button" :disabled="saving" :aria-label="`删除${node.goal.title}`" title="删除目标" @click="emit('remove', node.goal.id)"><Trash2 :size="16" aria-hidden="true" /></button>
          </div>
        </article>
      </div>
      <slot v-if="!layout" name="empty" />
    </div>
  </section>
</template>
