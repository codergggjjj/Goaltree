import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getApiProblem, goalApi } from '@/features/goals/goalApi'
import type {
  ApiProblem,
  CreateGoalInput,
  GoalDetail,
  GoalListItem,
  TrashGoalItem,
  UpdateGoalInput,
} from '@/features/goals/types'

export const useGoalsStore = defineStore('goals', () => {
  const goals = ref<GoalListItem[]>([])
  const currentGoal = ref<GoalDetail | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<ApiProblem | null>(null)
  const trash = ref<TrashGoalItem[]>([])
  const trashLoading = ref(false)
  const trashError = ref<ApiProblem | null>(null)

  const activeGoals = computed(() => goals.value.filter((goal) => goal.status === 'ACTIVE'))

  async function fetchGoals() {
    loading.value = true
    error.value = null
    try {
      goals.value = await goalApi.list()
    } catch (caught) {
      error.value = getApiProblem(caught)
    } finally {
      loading.value = false
    }
  }

  async function fetchGoal(goalId: string) {
    loading.value = true
    error.value = null
    try {
      currentGoal.value = await goalApi.get(goalId)
      return currentGoal.value
    } catch (caught) {
      error.value = getApiProblem(caught)
    } finally {
      loading.value = false
    }
  }

  async function refreshCurrent() {
    if (currentGoal.value) {
      await fetchGoal(currentGoal.value.id)
    }
  }

  async function createRootGoal(payload: CreateGoalInput) {
    if (payload.parentId != null) {
      error.value = { code: 'ROOT_PARENT_NOT_ALLOWED', message: '新建主目标不能指定父目标。' }
      return null
    }
    return save(async () => {
      const goal = await goalApi.createRoot(payload)
      await fetchGoals()
      return goal
    })
  }

  async function createChildGoal(payload: CreateGoalInput, parentId: string) {
    if (!parentId.trim()) {
      error.value = { code: 'PARENT_REQUIRED', message: '请先从一个目标节点选择“添加子目标”。' }
      return null
    }
    return save(async () => {
      const goal = await goalApi.createChild({ ...payload, parentId })
      await fetchGoals()
      return goal
    })
  }

  async function updateGoal(goalId: string, payload: UpdateGoalInput) {
    return save(async () => {
      const goal = await goalApi.update(goalId, payload)
      currentGoal.value = goal
      await fetchGoals()
      return goal
    })
  }

  async function runAction(
    goalId: string,
    action: 'start' | 'pause' | 'resume' | 'complete' | 'tree-complete' | 'archive',
    version: number,
    reason?: string,
  ) {
    return save(async () => {
      const goal = await goalApi.action(goalId, action, version, reason)
      currentGoal.value = goal
      await fetchGoals()
      return goal
    })
  }

  async function deleteGoal(goalId: string, version: number) {
    return save(async () => {
      await goalApi.delete(goalId, version)
      if (currentGoal.value?.id === goalId) currentGoal.value = null
      await fetchGoals()
      return true
    })
  }

  async function deleteGoalTree(goalId: string, version: number) {
    return save(async () => {
      await goalApi.deleteTree(goalId, version)
      await fetchGoals()
      if (currentGoal.value && !goals.value.some((goal) => goal.id === currentGoal.value?.id)) {
        currentGoal.value = null
      }
      return true
    })
  }

  async function fetchTrash() {
    trashLoading.value = true
    trashError.value = null
    try {
      trash.value = await goalApi.listTrash()
      return trash.value
    } catch (caught) {
      trashError.value = getApiProblem(caught)
      return null
    } finally {
      trashLoading.value = false
    }
  }

  async function restoreGoal(goalId: string, version: number) {
    return save(async () => {
      await goalApi.restore(goalId, version)
      await fetchGoals()
      await fetchTrash()
      return true
    })
  }

  async function moveGoal(goalId: string, version: number, direction: 'UP' | 'DOWN') {
    return save(async () => {
      await goalApi.move(goalId, version, direction)
      await fetchGoals()
      return true
    })
  }

  async function save<T>(operation: () => Promise<T>): Promise<T | null> {
    saving.value = true
    error.value = null
    try {
      return await operation()
    } catch (caught) {
      error.value = getApiProblem(caught)
      return null
    } finally {
      saving.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    goals,
    currentGoal,
    loading,
    saving,
    error,
    trash,
    trashLoading,
    trashError,
    activeGoals,
    fetchGoals,
    fetchGoal,
    refreshCurrent,
    createRootGoal,
    createChildGoal,
    updateGoal,
    runAction,
    deleteGoal,
    deleteGoalTree,
    fetchTrash,
    restoreGoal,
    moveGoal,
    clearError,
  }
})
