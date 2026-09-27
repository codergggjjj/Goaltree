import type { GoalPriority, GoalStatus, MilestoneStatus, ProgressMode, Revision } from './types'

export const goalStatusLabels: Record<GoalStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
}

export const priorityLabels: Record<GoalPriority, string> = {
  HIGH: '高优先级',
  MEDIUM: '中优先级',
  LOW: '低优先级',
}

export const progressModeLabels: Record<ProgressMode, string> = {
  MILESTONE_WEIGHTED: '里程碑加权',
  SINGLE_METRIC: '单指标',
  MANUAL: '手动百分比',
}

export const milestoneStatusLabels: Record<MilestoneStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  ACHIEVED: '已达成',
  DELAYED: '已延期',
  CANCELLED: '已取消',
}

export function formatGoalDate(value?: string) {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

export function formatRevisionTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

const fieldLabels: Record<string, string> = {
  title: '目标名称',
  description: '目标说明',
  startDate: '开始日期',
  targetDate: '目标日期',
  priority: '优先级',
  status: '目标状态',
  progressMode: '进度模式',
  parentId: '父目标',
  completionCriteria: '完成标准',
  milestones: '里程碑',
  weight: '里程碑权重',
  acceptance: '验收结果',
}

export function revisionChanges(revision: Revision): string[] {
  if (!revision.before) {
    return [revision.entityType === 'GOAL' ? '建立目标初始版本' : '新增里程碑']
  }

  const changes = Object.keys({ ...revision.before, ...revision.after })
    .filter((key) => JSON.stringify(revision.before?.[key]) !== JSON.stringify(revision.after[key]))
    .filter((key) => fieldLabels[key])
    .map((key) => `${fieldLabels[key]}已更新`)

  if (changes.length > 0) return changes
  if (revision.action === 'STATUS_CHANGE') return ['状态已更新']
  return ['内容已更新']
}
