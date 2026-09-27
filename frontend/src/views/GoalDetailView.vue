<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import {
  Archive,
  ArrowLeft,
  Check,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Edit3,
  Flag,
  MoreHorizontal,
  Pause,
  Play,
  Plus,
  RotateCcw,
} from '@lucide/vue'
import GoalEditorSheet from '@/features/goals/GoalEditorSheet.vue'
import GoalLinks from '@/features/goals/GoalLinks.vue'
import { goalStatusLabels } from '@/features/goals/goalLabels'
import { getRootId, getSubtreeGoals } from '@/features/goals/goalTree'
import type { CreateGoalInput, UpdateGoalInput } from '@/features/goals/types'
import { useGoalsStore } from '@/stores/goals'

const route = useRoute()
const router = useRouter()
const store = useGoalsStore()
const { currentGoal: goal, goals, loading, saving, error } = storeToRefs(store)

const editorOpen = ref(false)
const childEditorOpen = ref(false)
const feedback = ref('')
const goalId = computed(() => String(route.params.id))
const treeGoals = computed(() => {
  const rootId = getRootId(goals.value, goalId.value)
  return rootId ? getSubtreeGoals(goals.value, rootId) : []
})

watch(
  goalId,
  async (id) => {
    await Promise.all([
      store.fetchGoal(id),
      goals.value.length ? Promise.resolve() : store.fetchGoals(),
    ])
  },
  { immediate: true },
)

async function runAction(action: 'start' | 'pause' | 'resume' | 'complete' | 'archive') {
  if (!goal.value) return
  if (action === 'archive' && !window.confirm('确定归档这个目标吗？')) return
  if (action === 'complete' && !window.confirm('确定标记为已完成吗？')) return
  const updated = await store.runAction(goal.value.id, action, goal.value.version)
  if (updated) {
    feedback.value =
      {
        start: '目标已开始',
        pause: '目标已暂停',
        resume: '目标已重新打开',
        complete: '目标已完成',
        archive: '目标已归档',
      }[action] ?? '已保存'
  }
}

async function createGoal(payload: CreateGoalInput) {
  const created = await store.createChildGoal(payload, goal.value?.id ?? '')
  if (created) {
    childEditorOpen.value = false
    feedback.value = '小目标已添加'
    await store.fetchGoal(goalId.value)
  }
}

async function updateGoal(id: string, payload: UpdateGoalInput) {
  const updated = await store.updateGoal(id, payload)
  if (updated) {
    editorOpen.value = false
    feedback.value = '目标已保存'
  }
}
</script>

<template>
  <div class="goal-detail-screen simple-detail-screen">
    <div v-if="loading && !goal" class="detail-loading" aria-label="正在加载目标">
      <div class="skeleton-line wide"></div>
      <div class="detail-skeleton-card"></div>
    </div>

    <div v-else-if="error && !goal" class="state-panel error-state" role="alert">
      <CircleAlert :size="26" aria-hidden="true" />
      <div>
        <strong>目标暂时无法打开</strong>
        <p>{{ error.message }}</p>
      </div>
      <button class="secondary-button" type="button" @click="router.push('/goals')">
        返回目标树
      </button>
    </div>

    <template v-else-if="goal">
      <button class="back-button" type="button" @click="router.push('/goals')">
        <ArrowLeft :size="19" aria-hidden="true" /> 返回目标树
      </button>

      <header class="simple-detail-header">
        <div class="simple-detail-title">
          <span class="status-badge" :class="goal.status.toLowerCase()">{{
            goalStatusLabels[goal.status]
          }}</span>
          <h1>{{ goal.title }}</h1>
          <RouterLink
            v-if="goal.parentTitle"
            class="parent-goal-link"
            :to="`/goals/${goal.parentId}`"
          >
            <Flag :size="15" aria-hidden="true" /> 上级目标：{{ goal.parentTitle }}
          </RouterLink>
        </div>

        <div class="detail-actions">
          <button
            v-if="goal.status !== 'ARCHIVED'"
            class="secondary-button"
            type="button"
            @click="editorOpen = true"
          >
            <Edit3 :size="17" aria-hidden="true" /> 编辑
          </button>
          <button
            v-if="goal.status === 'DRAFT'"
            class="primary-button"
            type="button"
            :disabled="saving"
            @click="runAction('start')"
          >
            <Play :size="17" aria-hidden="true" /> 开始
          </button>
          <button
            v-else-if="goal.status === 'ACTIVE'"
            class="primary-button"
            type="button"
            :disabled="saving"
            @click="runAction('complete')"
          >
            <CircleCheck :size="18" aria-hidden="true" /> 完成
          </button>
          <button
            v-else-if="['PAUSED', 'COMPLETED', 'ARCHIVED'].includes(goal.status)"
            class="primary-button"
            type="button"
            :disabled="saving"
            @click="runAction('resume')"
          >
            <RotateCcw :size="17" aria-hidden="true" /> 重新打开
          </button>
          <details class="action-menu">
            <summary aria-label="更多目标操作">
              <MoreHorizontal :size="21" aria-hidden="true" />
            </summary>
            <div>
              <button v-if="goal.status === 'ACTIVE'" type="button" @click="runAction('pause')">
                <Pause :size="17" aria-hidden="true" /> 暂停
              </button>
              <button
                v-if="goal.status !== 'ARCHIVED'"
                type="button"
                @click="childEditorOpen = true"
              >
                <Plus :size="17" aria-hidden="true" /> 添加小目标
              </button>
              <button
                v-if="goal.status !== 'ARCHIVED'"
                class="danger-action"
                type="button"
                @click="runAction('archive')"
              >
                <Archive :size="17" aria-hidden="true" /> 归档
              </button>
            </div>
          </details>
        </div>
      </header>

      <div v-if="feedback" class="app-toast" role="status">
        <Check :size="18" aria-hidden="true" /><span>{{ feedback }}</span>
        <button type="button" aria-label="关闭提示" @click="feedback = ''">×</button>
      </div>
      <div v-if="error" class="inline-error" role="alert">
        <CircleAlert :size="18" aria-hidden="true" /><span>{{ error.message }}</span>
        <button type="button" @click="store.clearError">知道了</button>
      </div>

      <section class="simple-record-section subgoal-section" aria-labelledby="subgoals-title">
        <div class="simple-section-heading">
          <div>
            <small>SMALL GOALS</small>
            <h2 id="subgoals-title">小目标</h2>
          </div>
          <button
            v-if="goal.status !== 'ARCHIVED'"
            class="text-button"
            type="button"
            @click="childEditorOpen = true"
          >
            <Plus :size="17" aria-hidden="true" /> 添加小目标
          </button>
        </div>

        <div v-if="goal.childGoals.length" class="simple-record-list subgoal-list">
          <RouterLink
            v-for="child in goal.childGoals"
            :key="child.id"
            :to="`/goals/${child.id}`"
            class="simple-record-row subgoal-row"
          >
            <span class="record-dot" :class="child.status.toLowerCase()"></span>
            <span class="subgoal-copy">
              <strong>{{ child.title }}</strong>
              <small>{{ goalStatusLabels[child.status] }}</small>
            </span>
            <ChevronRight :size="19" aria-hidden="true" />
          </RouterLink>
        </div>

        <button
          v-else-if="goal.status !== 'ARCHIVED'"
          class="simple-empty-action"
          type="button"
          @click="childEditorOpen = true"
        >
          <Plus :size="20" aria-hidden="true" /> 添加第一个小目标
        </button>
        <p v-else class="simple-empty-copy">还没有小目标</p>
      </section>

      <section class="simple-record-section" aria-label="关联链接">
        <GoalLinks :goal-id="goal.id" />
      </section>

      <GoalEditorSheet
        :open="editorOpen"
        :goal="goal"
        :goals="treeGoals"
        planning-mode="edit"
        :saving="saving"
        :server-error="error"
        @close="editorOpen = false"
        @create="createGoal"
        @update="updateGoal"
      />
      <GoalEditorSheet
        :open="childEditorOpen"
        :goals="treeGoals"
        :initial-parent-id="goal.id"
        planning-mode="child"
        :saving="saving"
        :server-error="error"
        @close="childEditorOpen = false"
        @create="createGoal"
        @update="updateGoal"
      />
    </template>
  </div>
</template>
