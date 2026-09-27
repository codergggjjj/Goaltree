import { test, expect } from '@playwright/test'

// See here how to get started:
// https://playwright.dev/docs/intro
test('opens the lightweight goal tree from the app root', async ({ page }) => {
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: [] }))
  await page.goto('/')
  await expect(page).toHaveTitle(/Goaltree/)
  await expect(page).toHaveURL(/\/goals$/)
  await expect(page.locator('.goal-tree-canvas')).toBeVisible()
  await expect(page.locator('.app-topbar, .desktop-rail, .mobile-tabbar')).toHaveCount(0)
  await expect(page.getByText('从一个大目标开始')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新建主目标' })).toBeVisible()
  await expect(page.getByText('复盘', { exact: true })).toHaveCount(0)
  await expect(page.getByText('实际进度', { exact: true })).toHaveCount(0)
})

test('opens goal details and the edit sheet', async ({ page }) => {
  let childTitle = ''
  let createdParentId: unknown = 'not-created'
  const goal = {
    id: 'demo-data-analysis-goal', title: '完成数据分析课程', status: 'ACTIVE',
    startDate: '2026-01-01', targetDate: '2046-01-01', priority: 'MEDIUM',
    progressMode: 'MANUAL', currentProgress: 0, version: 1, criterionCount: 0,
    satisfiedCriterionCount: 0, milestoneCount: 0, updatedAt: '2026-01-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z', description: '', childGoals: [],
    completionCriteria: [], milestones: [], dailyTasks: [],
  }
  await page.route(/\/api\/goals(?:\/|\?|$)/, (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const child = { ...goal, id: 'detail-child', title: childTitle, parentId: goal.id }
    if (request.method() === 'POST' && url.pathname === '/api/goals') {
      const body = request.postDataJSON()
      childTitle = body.title
      createdParentId = body.parentId
      return route.fulfill({ status: 201, json: { ...child, ...body, title: childTitle } })
    }
    if (url.pathname === '/api/goals') {
      return route.fulfill({ json: childTitle ? [goal, child] : [goal] })
    }
    if (url.pathname.endsWith('/links')) return route.fulfill({ json: [] })
    return route.fulfill({ json: { ...goal, childGoals: childTitle ? [child] : [] } })
  })
  await page.goto('/goals')
  await expect(page.locator('.goal-tree-canvas')).toBeVisible()
  await expect(page.getByRole('button', { name: '新建主目标' })).toBeVisible()
  await page.goto('/goals/demo-data-analysis-goal')

  await expect(page.getByRole('heading', { level: 1, name: /完成数据分析课程/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '小目标' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '关联链接' })).toBeVisible()
  await expect(page.getByText('备注')).toHaveCount(0)
  await expect(page.getByText('权重', { exact: true })).toHaveCount(0)
  await expect(page.getByText('目标日期', { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '编辑目标' })).toBeVisible()
  await expect(page.getByLabel('目标名称')).toHaveValue(/完成数据分析课程/)
  await expect(page.getByLabel('备注')).toHaveCount(0)
  await expect(page.getByLabel(/开始日期/)).toHaveCount(0)
  await page.getByRole('button', { name: '关闭目标编辑器' }).click()
  await expect(page.getByRole('dialog', { name: '编辑目标' })).toBeHidden()

  await page.getByRole('button', { name: '添加第一个小目标' }).click()
  await expect(page.getByRole('dialog', { name: '添加子目标' })).toBeVisible()
  await expect(page.getByLabel('目标名称')).toBeFocused()
  await expect(page.getByText('父目标：完成数据分析课程')).toBeVisible()
  await page.getByLabel('目标名称').fill('课程笔记')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('link', { name: /课程笔记/ })).toBeVisible()
  expect(createdParentId).toBe(goal.id)
})
