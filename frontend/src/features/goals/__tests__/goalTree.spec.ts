import { describe, expect, it } from 'vitest'
import { buildGoalTree, getGoalRoots, getGoalTreeBranches, getMainGoals, getNextActions, getSubtreeGoals, layoutTree, leafNodes } from '../goalTree'
import type { GoalDetail, GoalListItem } from '../types'

const root: GoalListItem = {
  id: 'root',
  title: '主目标',
  startDate: '2026-01-01',
  targetDate: '2026-12-31',
  priority: 'HIGH',
  status: 'ACTIVE',
  progressMode: 'MILESTONE_WEIGHTED',
  currentProgress: 40,
  version: 1,
  criterionCount: 1,
  satisfiedCriterionCount: 0,
  milestoneCount: 1,
  updatedAt: '2026-01-01T00:00:00Z',
}

const child: GoalListItem = {
  ...root,
  id: 'child',
  parentId: 'root',
  title: '子目标',
  targetDate: '2026-06-30',
}

describe('goal tree mapping', () => {
  it('keeps archived main goals and their descendants in separate trees', () => {
    const archived = { ...root, id: 'archived', title: '归档主目标', status: 'ARCHIVED' as const }
    const archivedChild = { ...child, id: 'archived-child', parentId: 'archived' }
    const all = [root, child, archived, archivedChild]
    expect(getMainGoals(all).map((goal) => goal.id)).toContain('archived')
    expect(getSubtreeGoals(all, 'root').map((goal) => goal.id)).toEqual(['root', 'child'])
    expect(getSubtreeGoals(all, 'archived').map((goal) => goal.id)).toEqual(['archived', 'archived-child'])
    expect(buildGoalTree(all, 'archived')?.children[0]?.goal.id).toBe('archived-child')
  })

  it('keeps hierarchy roots and promotes a child only when its parent is filtered out', () => {
    expect(getGoalRoots([root, child], 'ALL').map((goal) => goal.id)).toEqual(['root'])

    const pausedChild = { ...child, status: 'PAUSED' as const }
    expect(getGoalRoots([root, pausedChild], 'PAUSED').map((goal) => goal.id)).toEqual(['child'])
  })

  it('maps child goals as the visible small goals', () => {
    const detail = {
      ...root,
      description: '',
      createdAt: '2026-01-01T00:00:00Z',
      completionCriteria: [],
      milestones: [
        {
          id: 'milestone',
          title: '里程碑',
          acceptance: '完成',
          targetDate: '2026-03-01',
          weight: 100,
          progress: 0,
          status: 'NOT_STARTED' as const,
          sortOrder: 1,
          version: 1,
        },
      ],
      childGoals: [child],
    } satisfies GoalDetail

    expect(getGoalTreeBranches(detail)).toMatchObject([
      { id: 'child', kind: 'GOAL', route: '/goals/child' },
    ])
  })

  it('does not treat legacy milestones as small goals', () => {
    const detail = {
      ...root,
      description: '',
      createdAt: '2026-01-01T00:00:00Z',
      completionCriteria: [],
      milestones: [
        {
          id: 'second',
          title: '第二阶段',
          acceptance: '完成',
          targetDate: '2026-06-01',
          weight: 50,
          progress: 0,
          status: 'NOT_STARTED' as const,
          sortOrder: 2,
          version: 1,
        },
        {
          id: 'first',
          title: '第一阶段',
          acceptance: '完成',
          targetDate: '2026-03-01',
          weight: 50,
          progress: 100,
          status: 'ACHIEVED' as const,
          sortOrder: 1,
          version: 1,
        },
      ],
      childGoals: [],
    } satisfies GoalDetail

    expect(getGoalTreeBranches(detail)).toEqual([])
  })

  it('derives leaf tasks and progress across every level without duplicating records', () => {
    const group = { ...child, id: 'group', title: '学习后端' }
    const taskA = { ...child, id: 'a', parentId: 'group', status: 'COMPLETED' as const }
    const taskB = { ...child, id: 'b', parentId: 'group', status: 'ACTIVE' as const }
    const tree = buildGoalTree([root, group, taskA, taskB], 'root')!
    expect(leafNodes(tree).map((node) => node.goal.id)).toEqual(['a', 'b'])
    expect(tree.progress).toBe(50)
    const layout = layoutTree(tree)
    expect(layout.nodes).toHaveLength(4)
    expect(layout.edges).toHaveLength(3)
    expect(layout.nodes.find((node) => node.goal.id === 'a')!.y).not.toBe(layout.nodes.find((node) => node.goal.id === 'b')!.y)
  })

  it('derives next actions only from unfinished leaves in the selected tree', () => {
    const group = { ...child, id: 'group', title: '学习后端', sortOrder: 0 }
    const task = { ...child, id: 'task', parentId: 'group', title: '数据库', sortOrder: 0 }
    const done = { ...child, id: 'done', parentId: 'group', status: 'COMPLETED' as const, sortOrder: 1 }
    const archived = { ...child, id: 'archived', parentId: 'root', status: 'ARCHIVED' as const }
    const otherRoot = { ...root, id: 'other-root' }
    const otherTask = { ...child, id: 'other-task', parentId: 'other-root' }
    const all = [root, group, task, done, archived, otherRoot, otherTask]

    expect(getNextActions(all, 'root')).toMatchObject([
      { goal: { id: 'task' }, path: ['学习后端'] },
    ])
    expect(getNextActions(all, 'other-root').map((item) => item.goal.id)).toEqual(['other-task'])
    expect(getNextActions([{ ...root, status: 'ARCHIVED' }, group, task], 'root')).toEqual([])
  })

  it('hides every descendant of a collapsed branch without changing its progress', () => {
    const group = { ...child, id: 'group', title: '学习后端' }
    const taskA = { ...child, id: 'a', parentId: 'group', status: 'COMPLETED' as const }
    const taskB = { ...child, id: 'b', parentId: 'group', status: 'ACTIVE' as const }
    const sibling = { ...child, id: 'sibling', parentId: 'root' }
    const tree = buildGoalTree([root, group, taskA, taskB, sibling], 'root')!
    const collapsed = layoutTree(tree, new Set(['group']))

    expect(collapsed.nodes.map((node) => node.goal.id)).toEqual(['group', 'sibling', 'root'])
    expect(collapsed.edges.map((edge) => edge.to.goal.id)).toEqual(['group', 'sibling'])
    expect(tree.progress).toBe(33)
    expect(tree.children[0]?.children).toHaveLength(2)
    expect(layoutTree(tree).nodes).toHaveLength(5)
  })

  it('stops an invalid cycle while building a tree', () => {
    const circular = { ...root, parentId: 'child' }
    expect(buildGoalTree([circular, child], 'root')?.children[0]?.children).toEqual([])
  })
})
