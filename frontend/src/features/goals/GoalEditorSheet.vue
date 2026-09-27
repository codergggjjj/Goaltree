<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { X } from '@lucide/vue'
import type {
  ApiProblem,
  CreateGoalInput,
  GoalDetail,
  GoalListItem,
  UpdateGoalInput,
} from './types'

const props = defineProps<{
  open: boolean
  goal?: GoalDetail | null
  goals: GoalListItem[]
  initialParentId?: string
  planningMode: 'root' | 'child' | 'edit'
  saving?: boolean
  serverError?: ApiProblem | null
}>()

const emit = defineEmits<{
  close: []
  create: [payload: CreateGoalInput]
  update: [goalId: string, payload: UpdateGoalInput]
}>()

const form = reactive({ title: '', parentId: '' })
const errors = ref<Record<string, string>>({})
const titleInput = ref<HTMLInputElement | null>(null)
const editing = computed(() => Boolean(props.goal))
const dialogTitle = computed(() =>
  editing.value ? '编辑目标' : props.planningMode === 'root' ? '新建主目标' : '添加子目标',
)
const parentTitle = computed(() => props.goals.find((goal) => goal.id === props.initialParentId)?.title)
const fixedParent = computed(() => props.planningMode === 'root' || props.planningMode === 'child'
  || (props.planningMode === 'edit' && !props.goal?.parentId))
const availableParents = computed(() =>
  props.goals.filter((goal) => goal.status !== 'ARCHIVED' && !wouldCreateCycle(goal.id)),
)

function wouldCreateCycle(parentId: string) {
  if (!props.goal) return false
  const byId = new Map(props.goals.map((goal) => [goal.id, goal]))
  const seen = new Set<string>()
  let cursor: string | undefined = parentId
  while (cursor && !seen.has(cursor)) {
    if (cursor === props.goal.id) return true
    seen.add(cursor)
    cursor = byId.get(cursor)?.parentId
  }
  return false
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    form.title = props.goal?.title ?? ''
    form.parentId = props.planningMode === 'root' ? '' : props.goal?.parentId ?? props.initialParentId ?? ''
    errors.value = props.planningMode === 'child' && !props.initialParentId
      ? { parentId: '请先从一个目标节点选择“添加子目标”。' }
      : {}
    void nextTick(() => titleInput.value?.focus())
  },
)

function submit() {
  if (!form.title.trim()) {
    errors.value = { title: '写下目标名称。' }
    return
  }

  if (props.planningMode === 'child' && !props.initialParentId) {
    errors.value = { parentId: '请先从一个目标节点选择“添加子目标”。' }
    return
  }

  if (props.goal?.parentId && !form.parentId) {
    errors.value = { parentId: '子目标必须保留在所属主目标树内。' }
    return
  }

  if (props.goal) {
    emit('update', props.goal.id, {
      version: props.goal.version,
      title: form.title.trim(),
      priority: props.goal.priority,
      progressMode: props.goal.progressMode,
      parentId: props.goal.parentId ? form.parentId : undefined,
      clearParent: !props.goal.parentId,
      changeReason: '更新目标记录',
    })
    return
  }

  emit('create', {
    title: form.title.trim(),
    priority: 'MEDIUM',
    progressMode: 'MANUAL',
    parentId: props.planningMode === 'root' ? undefined : props.initialParentId,
    completionCriteria: [
      {
        type: 'BOOLEAN',
        description: '目标已完成',
        required: false,
      },
    ],
    milestones: [],
    startImmediately: true,
  })
}

function close() {
  if (!props.saving) emit('close')
}

</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="sheet-overlay"
      role="presentation"
      @mousedown.self="close"
      @keydown.esc="close"
    >
      <section
        class="app-sheet compact-sheet simple-sheet"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`goal-editor-title-${editing ? 'edit' : 'create'}`"
      >
        <header class="sheet-header">
          <h2 :id="`goal-editor-title-${editing ? 'edit' : 'create'}`">{{ dialogTitle }}</h2>
          <button class="icon-button" type="button" aria-label="关闭目标编辑器" @click="close">
            <X :size="22" aria-hidden="true" />
          </button>
        </header>

        <form class="sheet-form single-column simple-form" @submit.prevent="submit">
          <div class="form-field">
            <label for="goal-title">目标名称</label>
            <input
              id="goal-title"
              ref="titleInput"
              v-model="form.title"
              maxlength="80"
              autocomplete="off"
              placeholder="例如：学会画人物插画"
            />
            <p v-if="errors.title" class="field-error" role="alert">{{ errors.title }}</p>
          </div>

          <div v-if="!fixedParent" class="form-field">
            <label for="goal-parent">放在哪个目标下面</label>
            <select id="goal-parent" v-model="form.parentId">
              <option v-for="parent in availableParents" :key="parent.id" :value="parent.id">
                {{ parent.title }}
              </option>
            </select>
          </div>
          <div v-else-if="planningMode === 'child'" class="goal-editor-context">
            <p>父目标：{{ parentTitle ?? '尚未选择目标节点' }}</p>
            <p v-if="errors.parentId" class="field-error" role="alert">{{ errors.parentId }}</p>
          </div>

          <div v-if="serverError" class="form-alert" role="alert">
            <strong>暂时无法保存</strong><span>{{ serverError.message }}</span>
          </div>

          <footer class="sheet-actions">
            <button class="secondary-button" type="button" :disabled="saving" @click="close">
              取消
            </button>
            <button class="primary-button" type="submit" :disabled="saving || (planningMode === 'child' && !initialParentId)">
              {{ saving ? '正在保存…' : '保存' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>
