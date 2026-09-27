import type { GoalDetail, GoalListItem, GoalStatus } from './types'

export type GoalTreeFilter = 'ALL' | GoalStatus
export const TREE_CARD_WIDTH = 270
function nodeHeight(goal: GoalListItem, hasChildren: boolean) {
  const titleUnits = [...goal.title].reduce((sum, character) => sum + (character.codePointAt(0)! > 0x2e80 ? 1 : .55), 0)
  const lines = Math.max(1, Math.ceil(titleUnits / 10.5))
  return (hasChildren ? 84 : 64) + (lines - 1) * 27
}
export function compareSiblingGoals(a: GoalListItem, b: GoalListItem) {
  return (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
    || a.title.localeCompare(b.title, 'zh-CN')
    || a.id.localeCompare(b.id)
}
export interface TreeNode {
  goal: GoalListItem
  children: TreeNode[]
  x: number
  y: number
  height: number
  progress: number
  complete: boolean
}

export function getGoalRoots(goals: GoalListItem[], filter: GoalTreeFilter) {
  const visible = goals.filter((goal) => goal.status !== 'ARCHIVED' && (filter === 'ALL' || goal.status === filter))
  const ids = new Set(visible.map((goal) => goal.id))
  return visible.filter((goal) => !goal.parentId || !ids.has(goal.parentId))
    .sort(compareSiblingGoals)
}

export function getMainGoals(goals: GoalListItem[]) {
  return goals.filter((goal) => !goal.parentId)
    .sort(compareSiblingGoals)
}

export function getRootId(goals: GoalListItem[], id: string): string | null {
  const byId = new Map(goals.map((goal) => [goal.id, goal]))
  const seen = new Set<string>()
  let cursor = byId.get(id)
  while (cursor && !seen.has(cursor.id)) {
    seen.add(cursor.id)
    if (!cursor.parentId) return cursor.id
    cursor = byId.get(cursor.parentId)
  }
  return null
}

export function getSubtreeGoals(goals: GoalListItem[], rootId: string) {
  const byId = new Map(goals.map((goal) => [goal.id, goal]))
  const children = new Map<string, GoalListItem[]>()
  for (const goal of goals) {
    if (!goal.parentId) continue
    const group = children.get(goal.parentId) ?? []
    group.push(goal)
    children.set(goal.parentId, group)
  }
  const found: GoalListItem[] = []
  const seen = new Set<string>()
  const visit = (id: string) => {
    if (seen.has(id)) return
    seen.add(id)
    const goal = byId.get(id)
    if (!goal) return
    found.push(goal)
    for (const child of children.get(id) ?? []) visit(child.id)
  }
  visit(rootId)
  return found
}

export function buildGoalTree(goals: GoalListItem[], rootId: string): TreeNode | null {
  const available = goals
  const byId = new Map(available.map((goal) => [goal.id, goal]))
  const children = new Map<string, GoalListItem[]>()
  for (const goal of available) {
    if (goal.parentId && byId.has(goal.parentId)) {
      const group = children.get(goal.parentId) ?? []
      group.push(goal)
      children.set(goal.parentId, group)
    }
  }
  const visit = (id: string, path: Set<string>): TreeNode | null => {
    const goal = byId.get(id)
    if (!goal || path.has(id)) return null
    const next = new Set(path)
    next.add(id)
    const descendants = (children.get(id) ?? []).sort(compareSiblingGoals)
      .map((child) => visit(child.id, next)).filter((node): node is TreeNode => node !== null)
    const leaves = descendants.flatMap(leafNodes)
    const complete = goal.status === 'COMPLETED' && descendants.every((child) => child.complete)
    const rawProgress = descendants.length ? Math.round(leaves.filter((leaf) => leaf.complete).length / leaves.length * 100) : (complete ? 100 : 0)
    const progress = complete ? 100 : Math.min(rawProgress, 99)
    return { goal, children: descendants, x: 0, y: 0, height: nodeHeight(goal, descendants.length > 0), progress, complete }
  }
  return visit(rootId, new Set())
}

export function leafNodes(node: TreeNode): TreeNode[] {
  return node.children.length ? node.children.flatMap(leafNodes) : [node]
}

export interface NextAction {
  goal: GoalListItem
  path: string[]
}

export function getNextActions(goals: GoalListItem[], rootId: string): NextAction[] {
  const root = buildGoalTree(goals, rootId)
  if (!root) return []
  const actions: NextAction[] = []
  const visit = (node: TreeNode, path: string[]) => {
    if (node.goal.status === 'ARCHIVED') return
    if (!node.children.length) {
      if (!node.complete) actions.push({ goal: node.goal, path })
      return
    }
    for (const child of node.children) {
      visit(child, node.goal.id === rootId ? [] : [...path, node.goal.title])
    }
  }
  visit(root, [])
  return actions
}

export function descendantCount(node: TreeNode): number {
  return node.children.reduce((count, child) => count + 1 + descendantCount(child), 0)
}

export function layoutTree(root: TreeNode, collapsedIds: ReadonlySet<string> = new Set()) {
  const nodes: TreeNode[] = []
  const edges: { from: TreeNode, to: TreeNode }[] = []
  let cursor = 40
  const walk = (node: TreeNode, depth: number) => {
    node.x = 40 + depth * 340
    const visibleChildren = collapsedIds.has(node.goal.id) ? [] : node.children
    visibleChildren.forEach((child) => {
      walk(child, depth + 1)
      edges.push({ from: node, to: child })
    })
    if (visibleChildren.length) {
      const first = visibleChildren[0]!
      const last = visibleChildren[visibleChildren.length - 1]!
      node.y = (first.y + first.height / 2 + last.y + last.height / 2) / 2 - node.height / 2
    } else {
      node.y = cursor
      cursor += node.height + 64
    }
    nodes.push(node)
  }
  walk(root, 0)
  return { nodes, edges, width: Math.max(...nodes.map((node) => node.x)) + TREE_CARD_WIDTH + 40, height: Math.max(...nodes.map((node) => node.y + node.height)) + 40 }
}

// Kept for detail-page compatibility; the planning view builds from the full list.
export function getGoalTreeBranches(detail: GoalDetail | null) {
  return (detail?.childGoals ?? []).map((goal) => ({
    id: goal.id, kind: 'GOAL' as const, title: goal.title, status: goal.status,
    route: `/goals/${goal.id}`,
  }))
}
