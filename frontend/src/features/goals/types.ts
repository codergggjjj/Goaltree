export type GoalStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'
export interface CanvasView { scale: number, centerX: number, centerY: number }
export type GoalPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type ProgressMode = 'MILESTONE_WEIGHTED' | 'SINGLE_METRIC' | 'MANUAL'
export type CriterionType = 'BOOLEAN' | 'METRIC' | 'ACCEPTANCE'
export type MilestoneStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'ACHIEVED' | 'DELAYED' | 'CANCELLED'

export interface CriterionInput {
  id?: string
  type: CriterionType
  description: string
  targetValue?: number
  unit?: string
  required: boolean
}

export interface MilestoneInput {
  id?: string
  title: string
  acceptance: string
  targetDate: string
  weight: number
  sortOrder?: number
}

export interface CreateGoalInput {
  title: string
  description?: string
  priority: GoalPriority
  progressMode: ProgressMode
  parentId?: string
  metricName?: string
  metricUnit?: string
  metricStartValue?: number
  metricTargetValue?: number
  completionCriteria: CriterionInput[]
  milestones: MilestoneInput[]
  startImmediately: boolean
}

export interface UpdateGoalInput {
  version: number
  title: string
  description?: string
  priority: GoalPriority
  progressMode: ProgressMode
  parentId?: string
  clearParent: boolean
  metricName?: string
  metricUnit?: string
  metricStartValue?: number
  metricTargetValue?: number
  completionCriteria?: CriterionInput[]
  changeReason?: string
}

export interface CompletionCriterion {
  id: string
  type: CriterionType
  description: string
  targetValue?: number
  unit?: string
  required: boolean
  satisfied: boolean
  satisfiedAt?: string
}

export interface Milestone {
  id: string
  title: string
  acceptance: string
  targetDate: string
  weight: number
  progress: number
  status: MilestoneStatus
  completedAt?: string
  sortOrder: number
  version: number
}

export interface GoalListItem {
  id: string
  parentId?: string
  title: string
  startDate: string
  targetDate: string
  priority: GoalPriority
  status: GoalStatus
  progressMode: ProgressMode
  currentProgress: number
  sortOrder?: number
  version: number
  criterionCount: number
  satisfiedCriterionCount: number
  milestoneCount: number
  nextMilestoneTitle?: string
  nextMilestoneDate?: string
  updatedAt: string
}

export interface TrashGoalItem {
  id: string
  parentId?: string
  title: string
  deletedAt: string
  descendantCount: number
  version: number
  canRestore: boolean
}

export interface GoalLink {
  id: string
  name: string
  url: string
}

export interface GoalLinkInput {
  name: string
  url: string
}

export interface GoalDetail extends Omit<
  GoalListItem,
  'criterionCount' | 'satisfiedCriterionCount' | 'milestoneCount'
> {
  parentTitle?: string
  description?: string
  metricName?: string
  metricUnit?: string
  metricStartValue?: number
  metricTargetValue?: number
  metricCurrentValue?: number
  pausedAt?: string
  completedAt?: string
  archivedAt?: string
  createdAt: string
  completionCriteria: CompletionCriterion[]
  milestones: Milestone[]
  childGoals: GoalListItem[]
}

export interface Revision {
  id: string
  entityType: 'GOAL' | 'MILESTONE'
  entityId: string
  revisionNo: number
  action: 'CREATE' | 'UPDATE' | 'STATUS_CHANGE' | 'CORRECTION'
  reason?: string
  effectiveAt: string
  before?: Record<string, unknown>
  after: Record<string, unknown>
  createdAt: string
}

export interface ApiProblem {
  code: string
  message: string
  currentVersion?: number
  fieldErrors?: Record<string, string>
}
