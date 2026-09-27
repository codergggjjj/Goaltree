<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ArrowDown, ArrowUp, CircleAlert, Flag, ListTodo, PanelLeftClose, PanelLeftOpen, Plus, Search, Trash2, X } from '@lucide/vue'
import GoalEditorSheet from '@/features/goals/GoalEditorSheet.vue'
import GoalLinkDialog from '@/features/goals/GoalLinkDialog.vue'
import GoalNextDrawer from '@/features/goals/GoalNextDrawer.vue'
import GoalTrashDrawer from '@/features/goals/GoalTrashDrawer.vue'
import GoalTreeCanvas from '@/features/goals/GoalTreeCanvas.vue'
import { compareSiblingGoals, getMainGoals, getNextActions, getRootId, getSubtreeGoals } from '@/features/goals/goalTree'
import type { CanvasView, CreateGoalInput, GoalDetail, GoalListItem, TrashGoalItem, UpdateGoalInput } from '@/features/goals/types'
import { useGoalsStore } from '@/stores/goals'

const selectedKey = 'goalflow:selected-main-goal:v1'
const viewsKey = 'goalflow:goal-tree-views:v2'
function readSelected() {
  try { return localStorage.getItem(selectedKey) ?? '' } catch { return '' }
}
function readViews(): Record<string, CanvasView> {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(viewsKey) ?? '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return value as Record<string, CanvasView>
  } catch { return {} }
}
function storeLocal(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* Private browsing may block storage. */ }
}

const store = useGoalsStore()
const { goals, loading, saving, error, trash, trashLoading, trashError } = storeToRefs(store)
const selectedGoalId = ref(readSelected())
const savedViews = ref(readViews())
const sidebarOpen = ref(true)
const openSidebarButton = ref<HTMLButtonElement | null>(null)
const closeSidebarButton = ref<HTMLButtonElement | null>(null)
const initialLoading = ref(true)
const editorOpen = ref(false)
const editorGoal = ref<GoalDetail | null>(null)
const editorMode = ref<'root' | 'child' | 'edit'>('root')
const parentId = ref('')
const editorLoading = ref(false)
const detailGoalId = ref('')
const nextDrawerOpen = ref(false)
const nextDrawerButton = ref<HTMLButtonElement | null>(null)
const trashOpen = ref(false)
const trashButton = ref<HTMLButtonElement | null>(null)
const trashActionError = ref('')
const recentlyDeleted = ref<{ id: string, title: string, wasRoot: boolean } | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined
const searchInput = ref<HTMLInputElement | null>(null)
const searchBox = ref<HTMLElement | null>(null)
const searchResultsElement = ref<HTMLElement | null>(null)
const searchQuery = ref('')
const searchOpen = ref(false)
const focusTarget = ref<{ id: string, rootId: string, serial: number } | null>(null)
let focusSerial = 0
const roots = computed(() => getMainGoals(goals.value))
const selected = computed(() => roots.value.find((goal) => goal.id === selectedGoalId.value) ?? null)
const detailGoal = computed(() => goals.value.find((goal) => goal.id === detailGoalId.value) ?? null)
const detailSiblings = computed(() => detailGoal.value
  ? goals.value.filter((goal) => (goal.parentId ?? null) === (detailGoal.value?.parentId ?? null)).sort(compareSiblingGoals)
  : [])
const detailIndex = computed(() => detailSiblings.value.findIndex((goal) => goal.id === detailGoalId.value))
const treeGoals = computed(() => selected.value ? getSubtreeGoals(goals.value, selected.value.id) : [])
const nextActions = computed(() => selected.value ? getNextActions(treeGoals.value, selected.value.id) : [])
const savedView = computed(() => selected.value ? savedViews.value[selected.value.id] ?? null : null)
const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  if (!query) return []
  const rootNames = new Map(roots.value.map((root) => [root.id, root.title]))
  return goals.value.filter((goal) => goal.title.toLocaleLowerCase().includes(query))
    .map((goal) => {
      const rootId = getRootId(goals.value, goal.id)
      return rootId ? { goal, rootId, rootTitle: rootNames.get(rootId) ?? goal.title } : null
    })
    .filter((result): result is { goal: GoalListItem, rootId: string, rootTitle: string } => result !== null)
    .slice(0, 10)
})

watch([roots, initialLoading], ([items, pending]) => {
  if (!pending && !items.some((goal) => goal.id === selectedGoalId.value)) {
    selectedGoalId.value = items[0]?.id ?? ''
  }
}, { immediate: true })
watch(selectedGoalId, (id) => storeLocal(selectedKey, id))

onMounted(async () => {
  window.addEventListener('keydown', searchShortcut)
  window.addEventListener('pointerdown', closeSearchOutside)
  await store.fetchGoals()
  initialLoading.value = false
})
onBeforeUnmount(() => {
  if (undoTimer) clearTimeout(undoTimer)
  window.removeEventListener('keydown', searchShortcut)
  window.removeEventListener('pointerdown', closeSearchOutside)
})

async function openTrash() {
  trashActionError.value = ''
  trashOpen.value = true
  await store.fetchTrash()
}
async function closeTrash() {
  trashOpen.value = false
  await nextTick()
  trashButton.value?.focus()
}
async function restore(item: TrashGoalItem) {
  trashActionError.value = ''
  if (await store.restoreGoal(item.id, item.version)) {
    if (!item.parentId) selectedGoalId.value = item.id
    if (recentlyDeleted.value?.id === item.id) recentlyDeleted.value = null
  } else {
    trashActionError.value = error.value?.message ?? '恢复失败，请重试。'
    await store.fetchTrash()
  }
}
async function undoDelete() {
  const pending = recentlyDeleted.value
  if (!pending) return
  const items = await store.fetchTrash()
  if (!items) {
    trashActionError.value = trashError.value?.message ?? '回收站暂时无法加载。'
    trashOpen.value = true
    return
  }
  const item = items.find((entry) => entry.id === pending.id)
  if (!item) {
    recentlyDeleted.value = null
    return
  }
  await restore(item)
  if (error.value) trashOpen.value = true
}

function closeSearchOutside(event: PointerEvent) {
  if (event.target instanceof Node && !searchBox.value?.contains(event.target)) searchOpen.value = false
}

function searchShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchInput.value?.focus()
  }
}
function focusFirstSearchResult() {
  searchResultsElement.value?.querySelector<HTMLButtonElement>('button')?.focus()
}
function closeSearch() {
  searchQuery.value = ''
  searchOpen.value = false
  searchInput.value?.focus()
}
function locateSearchResult(result: { goal: GoalListItem, rootId: string }) {
  selectedGoalId.value = result.rootId
  focusTarget.value = { id: result.goal.id, rootId: result.rootId, serial: ++focusSerial }
  searchQuery.value = ''
  searchOpen.value = false
}
async function closeNextDrawer() {
  nextDrawerOpen.value = false
  await nextTick()
  nextDrawerButton.value?.focus()
}
function locateNextAction(id: string) {
  if (!selected.value) return
  nextDrawerOpen.value = false
  focusTarget.value = { id, rootId: selected.value.id, serial: ++focusSerial }
}
function searchKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && searchResults.value[0]) {
    event.preventDefault()
    locateSearchResult(searchResults.value[0])
  } else if (event.key === 'ArrowDown' && searchResults.value.length) {
    event.preventDefault()
    focusFirstSearchResult()
  } else if (event.key === 'Escape') {
    closeSearch()
  }
}

async function refresh() { await store.fetchGoals() }
async function showSidebar() {
  sidebarOpen.value = true
  await nextTick()
  closeSidebarButton.value?.focus()
}
async function hideSidebar() {
  sidebarOpen.value = false
  await nextTick()
  openSidebarButton.value?.focus()
}
function selectRoot(id: string) {
  detailGoalId.value = ''
  selectedGoalId.value = id
  if (window.matchMedia('(max-width: 767px)').matches) void hideSidebar()
}
function rememberView(id: string, view: CanvasView) {
  savedViews.value = { ...savedViews.value, [id]: view }
  storeLocal(viewsKey, JSON.stringify(savedViews.value))
}
function forgetView(id: string) {
  const next = { ...savedViews.value }
  delete next[id]
  savedViews.value = next
  storeLocal(viewsKey, JSON.stringify(next))
}
function openCreateRoot() {
  editorGoal.value = null
  editorMode.value = 'root'
  parentId.value = ''
  editorOpen.value = true
}
function openDetails(id: string) {
  detailGoalId.value = id
}
function detailAction(action: 'add' | 'edit' | 'toggle' | 'remove') {
  const goal = detailGoal.value
  if (!goal) return
  detailGoalId.value = ''
  if (action === 'add') openCreateChild(goal.id)
  else if (action === 'edit') void openEdit(goal.id)
  else if (action === 'toggle') void toggle(goal)
  else void remove(goal.id)
}
async function closeDetails() {
  const id = detailGoalId.value
  detailGoalId.value = ''
  await nextTick()
  const card = [...document.querySelectorAll<HTMLElement>('.tree-card')].find((item) => item.dataset.goalId === id)
  card?.querySelector<HTMLAnchorElement>('a')?.focus({ preventScroll: true })
}
function openCreateChild(id: string) {
  editorGoal.value = null
  editorMode.value = 'child'
  parentId.value = treeGoals.value.some((goal) => goal.id === id) ? id : ''
  editorOpen.value = true
}
async function openEdit(id: string) {
  editorLoading.value = true
  store.clearError()
  const detail = await store.fetchGoal(id)
  editorLoading.value = false
  if (detail) {
    editorGoal.value = detail
    editorMode.value = 'edit'
    parentId.value = ''
    editorOpen.value = true
  }
}
async function create(payload: CreateGoalInput) {
  const isRoot = editorMode.value === 'root'
  const created = isRoot
    ? await store.createRootGoal(payload)
    : await store.createChildGoal(payload, parentId.value)
  if (created) {
    editorOpen.value = false
    if (isRoot) selectedGoalId.value = created.id
  }
}
async function update(id: string, payload: UpdateGoalInput) {
  if (await store.updateGoal(id, payload)) editorOpen.value = false
}
async function toggle(goal: GoalListItem) {
  const action = ['COMPLETED', 'PAUSED', 'ARCHIVED'].includes(goal.status) ? 'resume' : 'tree-complete'
  await store.runAction(goal.id, action, goal.version)
}
async function move(goal: GoalListItem, direction: 'UP' | 'DOWN') {
  await store.moveGoal(goal.id, goal.version, direction)
}
async function remove(id: string) {
  const item = goals.value.find((goal) => goal.id === id)
  if (!item) return
  const descendants = getSubtreeGoals(goals.value, id).length - 1
  const isRoot = !item.parentId
  const message = isRoot || descendants > 0
    ? `确定删除“${item.title}”及其全部 ${descendants} 个子目标吗？整棵目标树会一并删除。`
    : `确定删除“${item.title}”吗？`
  if (!window.confirm(message)) return
  const result = isRoot || descendants > 0
    ? await store.deleteGoalTree(id, item.version)
    : await store.deleteGoal(id, item.version)
  if (result) {
    if (isRoot) forgetView(id)
    recentlyDeleted.value = { id, title: item.title, wasRoot: isRoot }
    if (undoTimer) clearTimeout(undoTimer)
    undoTimer = setTimeout(() => { recentlyDeleted.value = null }, 10000)
  }
}
</script>

<template>
  <div class="goals-screen goal-tree-screen" :class="{ 'sidebar-open': sidebarOpen }">
    <h1 class="tree-accessible-title">目标树</h1>
    <button v-if="sidebarOpen" class="main-goal-backdrop" type="button" aria-label="隐藏主目标侧边栏" @click="hideSidebar"></button>
    <aside v-if="sidebarOpen" class="main-goal-sidebar" aria-label="主目标侧边栏" @keydown.esc="hideSidebar">
      <div class="main-goal-sidebar-head">
        <strong>主目标</strong>
        <button ref="closeSidebarButton" type="button" aria-label="隐藏主目标侧边栏" title="隐藏侧边栏" @click="hideSidebar"><PanelLeftClose :size="19" aria-hidden="true" /></button>
      </div>
      <div class="main-goal-sidebar-list" role="list" aria-label="主目标列表">
        <div v-for="(goal, index) in roots" :key="goal.id" class="main-goal-row" :class="{ selected: selectedGoalId === goal.id }" role="listitem">
          <button class="main-goal-select" type="button" :aria-current="selectedGoalId === goal.id ? 'true' : undefined" @click="selectRoot(goal.id)">
            <Flag :size="16" aria-hidden="true" />
            <span>{{ goal.title }}<small v-if="goal.status === 'ARCHIVED'">已归档</small></span>
          </button>
          <button class="main-goal-order" type="button" :disabled="saving || index === 0" :aria-label="`上移主目标${goal.title}`" title="上移" @click="move(goal, 'UP')"><ArrowUp :size="15" aria-hidden="true" /></button>
          <button class="main-goal-order" type="button" :disabled="saving || index === roots.length - 1" :aria-label="`下移主目标${goal.title}`" title="下移" @click="move(goal, 'DOWN')"><ArrowDown :size="15" aria-hidden="true" /></button>
          <button class="main-goal-delete" type="button" :disabled="saving" :aria-label="`删除主目标${goal.title}及其目标树`" title="删除主目标及目标树" @click="remove(goal.id)"><Trash2 :size="16" aria-hidden="true" /></button>
        </div>
        <p v-if="!initialLoading && !roots.length" class="main-goal-list-empty">还没有主目标</p>
      </div>
      <button class="primary-button main-goal-create" type="button" @click="openCreateRoot"><Plus :size="17" aria-hidden="true" />新建主目标</button>
      <button class="secondary-button main-goal-trash" type="button" @click="openTrash"><Trash2 :size="16" aria-hidden="true" />回收站</button>
    </aside>

    <GoalTreeCanvas
      :goal="selected"
      :goals="treeGoals"
      :saved-view="savedView"
      :focus-target="focusTarget"
      :saving="saving || editorLoading"
      @add="openCreateChild"
      @edit="openEdit"
      @remove="remove"
      @toggle="toggle"
      @open="openDetails"
      @view-change="rememberView"
    >
      <template #toolbar>
        <button v-if="!sidebarOpen" ref="openSidebarButton" class="main-goal-open" type="button" aria-label="展开主目标侧边栏" title="展开主目标" @click="showSidebar"><PanelLeftOpen :size="18" aria-hidden="true" />主目标</button>
        <span v-if="selected" class="current-main-goal" :title="selected.title">{{ selected.title }}</span>
        <button ref="nextDrawerButton" class="next-action-open" type="button" :disabled="!selected || initialLoading" :aria-label="`下一步做什么，${nextActions.length}项`" :aria-expanded="nextDrawerOpen" @click="nextDrawerOpen = true"><ListTodo :size="17" aria-hidden="true" />下一步 <small>{{ nextActions.length }}</small></button>
        <button ref="trashButton" class="next-action-open goal-trash-open" type="button" aria-label="打开回收站" :aria-expanded="trashOpen" @click="openTrash"><Trash2 :size="17" aria-hidden="true" />回收站</button>
        <div ref="searchBox" class="goal-search" role="search" @keydown.esc.stop="closeSearch">
          <Search :size="17" aria-hidden="true" />
          <input ref="searchInput" v-model="searchQuery" type="search" aria-label="搜索目标" placeholder="搜索并定位目标" autocomplete="off" :disabled="!goals.length" @focus="searchOpen = true" @keydown="searchKeydown" />
          <div v-if="searchOpen && searchQuery.trim()" class="goal-search-popover">
            <ul v-if="searchResults.length" ref="searchResultsElement" aria-label="搜索结果">
              <li v-for="result in searchResults" :key="result.goal.id">
                <button type="button" :aria-label="`定位${result.goal.title}，位于${result.rootTitle}`" @click="locateSearchResult(result)">
                  <span>{{ result.goal.title }}</span><small>{{ result.rootTitle }}</small>
                </button>
              </li>
            </ul>
            <p v-else role="status">没有找到匹配的目标</p>
          </div>
        </div>
      </template>
      <template #status>
        <div v-if="error && goals.length" class="tree-inline-error" role="alert">
          <CircleAlert :size="17" aria-hidden="true" />
          <span>{{ error.message }}</span>
          <button type="button" @click="refresh">重试</button>
          <button type="button" aria-label="关闭错误提示" @click="store.clearError"><X :size="15" aria-hidden="true" /></button>
        </div>
      </template>
      <template #empty>
        <div v-if="initialLoading || (loading && !goals.length)" class="tree-state" role="status">正在加载目标树…</div>
        <div v-else-if="error" class="tree-state" role="alert">
          <CircleAlert :size="28" aria-hidden="true" />
          <strong>目标暂时无法加载</strong>
          <p>{{ error.message }}</p>
          <button class="secondary-button" type="button" @click="refresh">重试</button>
        </div>
        <div v-else class="tree-state">
          <Flag :size="28" aria-hidden="true" />
          <strong>还没有主目标</strong>
          <p>在左侧新建主目标，开始规划。</p>
        </div>
      </template>
    </GoalTreeCanvas>

    <GoalNextDrawer :open="nextDrawerOpen" :root="selected" :actions="nextActions" @close="closeNextDrawer" @locate="locateNextAction" />
    <GoalTrashDrawer :open="trashOpen" :items="trash" :loading="trashLoading" :saving="saving" :error="trashError" :action-error="trashActionError" @close="closeTrash" @retry="store.fetchTrash" @restore="restore" />

    <div v-if="recentlyDeleted" class="goal-undo-toast" role="status" aria-live="polite">
      <span>已删除“{{ recentlyDeleted.title }}”{{ recentlyDeleted.wasRoot ? '及其目标树' : '' }}</span>
      <button type="button" :disabled="saving" @click="undoDelete">撤销</button>
      <button type="button" aria-label="关闭撤销提示" @click="recentlyDeleted = null"><X :size="16" aria-hidden="true" /></button>
    </div>

    <GoalLinkDialog :goal="detailGoal" :can-move-up="detailIndex > 0" :can-move-down="detailIndex >= 0 && detailIndex < detailSiblings.length - 1" :moving="saving" :order-error="error?.message" @close="closeDetails" @move="(direction) => detailGoal && move(detailGoal, direction)" @add="detailAction('add')" @edit="detailAction('edit')" @toggle="detailAction('toggle')" @remove="detailAction('remove')" />

    <GoalEditorSheet
      :open="editorOpen"
      :goal="editorGoal"
      :goals="treeGoals"
      :planning-mode="editorMode"
      :initial-parent-id="parentId"
      :saving="saving"
      :server-error="error"
      @close="editorOpen = false"
      @create="create"
      @update="update"
    />
  </div>
</template>
