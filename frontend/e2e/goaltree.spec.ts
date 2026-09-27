import { test, expect, type Page, type TestInfo } from '@playwright/test'

async function closeSidebarOnMobile(page: Page, testInfo: TestInfo) {
  if (testInfo.project.name === 'mobile-chromium') {
    await page.getByRole('button', { name: '隐藏主目标侧边栏' }).last().click()
  }
}

async function openSidebarIfHidden(page: Page) {
  if (!(await page.getByRole('complementary', { name: '主目标侧边栏' }).isVisible())) {
    await page.getByRole('button', { name: '展开主目标侧边栏' }).click()
  }
}

async function openTrashForViewport(page: Page, testInfo: TestInfo) {
  if (testInfo.project.name === 'mobile-chromium') {
    await page.getByRole('complementary', { name: '主目标侧边栏' }).getByRole('button', { name: '回收站' }).click()
  } else {
    await page.getByRole('button', { name: '打开回收站' }).click()
  }
}

const base = {
  startDate: '2026-01-01', targetDate: '2046-01-01', priority: 'MEDIUM',
  progressMode: 'MANUAL', currentProgress: 0, version: 1, criterionCount: 0,
  satisfiedCriterionCount: 0, milestoneCount: 0, updatedAt: '2026-01-01T00:00:00Z',
}
const initial = [
  { ...base, id: 'root', title: '成为全栈工程师', status: 'ACTIVE' },
  { ...base, id: 'frontend', parentId: 'root', title: '学习前端', status: 'ACTIVE' },
  { ...base, id: 'backend', parentId: 'root', title: '学习后端', status: 'ACTIVE' },
  { ...base, id: 'spring', parentId: 'backend', title: 'Spring Boot', status: 'ACTIVE' },
  { ...base, id: 'database', parentId: 'backend', title: '数据库', status: 'ACTIVE' },
]

test('full-window tree and canvas controls share one goal source', async ({ page }, testInfo) => {
  let goals = initial.map((goal) => ({ ...goal }))
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    if (request.method() === 'GET' && new URL(request.url()).pathname === '/api/goals') {
      await route.fulfill({ json: goals })
    } else if (request.method() === 'POST' && request.url().includes('/tree-complete')) {
      const id = new URL(request.url()).pathname.split('/')[3]
      goals = goals.map((goal) => goal.id === id ? { ...goal, status: 'COMPLETED', version: goal.version + 1 } : goal)
      await route.fulfill({ json: { ...goals.find((goal) => goal.id === id), childGoals: [], completionCriteria: [], milestones: [] } })
    } else await route.continue()
  })
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await expect(page.getByRole('link', { name: '查看Spring Boot详情' })).toBeVisible()
  await expect(page.locator('.tree-card')).toHaveCount(5)
  await expect(page.getByRole('tab', { name: /任务列表/ })).toHaveCount(0)
  await expect(page.locator('.app-topbar, .desktop-rail, .mobile-tabbar')).toHaveCount(0)
  await page.screenshot({ path: `../output/playwright/goaltree-${testInfo.project.name}.png`, fullPage: true })
  const viewport = page.locator('.tree-viewport')
  const world = page.locator('.tree-world')
  const viewportHeight = await viewport.evaluate((el) => el.getBoundingClientRect().height)
  expect(viewportHeight).toBeGreaterThan((await page.evaluate(() => window.innerHeight)) * .7)
  const initialTransform = await world.evaluate((el) => (el as HTMLElement).style.transform)
  await page.getByRole('button', { name: '重置视图' }).click()
  await expect(world).toHaveAttribute('style', /scale\(1\)/)
  await page.getByRole('button', { name: '适应画布' }).click()
  await expect(world).not.toHaveAttribute('style', /translate\(24px, 24px\) scale\(1\)/)
  await viewport.hover({ position: { x: 10, y: 10 } })
  await page.mouse.wheel(0, -200)
  await expect.poll(() => world.evaluate((el) => (el as HTMLElement).style.transform)).not.toBe(initialTransform)
  const beforeDrag = await world.evaluate((el) => (el as HTMLElement).style.transform)
  const box = await viewport.boundingBox()
  expect(box).not.toBeNull()
  const bounds = box as { x: number, y: number }
  await page.mouse.move(bounds.x + 20, bounds.y + 20)
  await page.mouse.down()
  await page.mouse.move(bounds.x + 65, bounds.y + 40)
  await page.mouse.up()
  await expect.poll(() => world.evaluate((el) => (el as HTMLElement).style.transform)).not.toBe(beforeDrag)
  await page.getByRole('button', { name: '完成Spring Boot' }).click()
  await expect(page.getByRole('button', { name: '重新打开Spring Boot' })).toBeVisible()
  await expect(page.locator('.tree-card.completed')).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy()
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1)).toBeTruthy()
})

test('minimal tree nodes keep titles readable and expose actions on focus or hover', async ({ page }, testInfo) => {
  const longTitle = '完成一个包含前端后端数据库和部署流程的全栈作品'
  await page.route(/\/api\/goals\/long\/links$/, (route) => route.fulfill({ json: [] }))
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: [
    ...initial,
    { ...base, id: 'long', parentId: 'backend', title: longTitle, status: 'ACTIVE' },
  ] }))
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await expect(page.locator('.tree-card-kind, .tree-card-date, .tree-card-progress, .tree-card-leaf-label')).toHaveCount(0)
  const shortNode = page.locator('.tree-card[data-goal-id="spring"]')
  const longNode = page.locator('.tree-card[data-goal-id="long"]')
  expect(await shortNode.evaluate((el) => el.getBoundingClientRect().height)).toBeLessThan(110)
  expect(await longNode.evaluate((el) => el.getBoundingClientRect().height)).toBeGreaterThan(await shortNode.evaluate((el) => el.getBoundingClientRect().height))
  expect(await longNode.evaluate((el) => {
    const content = el.querySelector('.tree-card-main') as HTMLElement
    return content.getBoundingClientRect().bottom <= el.getBoundingClientRect().bottom
  })).toBeTruthy()
  await longNode.hover()
  await expect(longNode.getByRole('button', { name: `为${longTitle}添加子目标` })).toBeVisible()
  await longNode.locator('.tree-card-main').click()
  const detail = page.getByRole('dialog', { name: `${longTitle}的目标详情` })
  await expect(detail.getByRole('button', { name: '添加子目标' })).toBeVisible()
  await detail.getByRole('button', { name: '添加子目标' }).click()
  await expect(page.getByRole('dialog', { name: '添加子目标' })).toBeVisible()
  await expect(page.getByText(`父目标：${longTitle}`)).toBeVisible()
})

test('next actions drawer follows the selected tree and locates hidden unfinished leaves', async ({ page }, testInfo) => {
  let goals = [
    ...initial.map((goal) => ({ ...goal })),
    { ...base, id: 'other-root', title: '写一本书', status: 'ACTIVE' },
    { ...base, id: 'other-task', parentId: 'other-root', title: '写提纲', status: 'ACTIVE' },
  ]
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && url.pathname === '/api/goals') return route.fulfill({ json: goals })
    if (request.method() === 'POST' && url.pathname.endsWith('/tree-complete')) {
      const id = url.pathname.split('/')[3]
      goals = goals.map((goal) => goal.id === id ? { ...goal, status: 'COMPLETED' as const, version: goal.version + 1 } : goal)
      return route.fulfill({ json: { ...goals.find((goal) => goal.id === id), childGoals: [], completionCriteria: [], milestones: [] } })
    }
    return route.continue()
  })
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await page.getByRole('button', { name: '收起学习后端的2个子目标' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(3)
  await page.getByRole('button', { name: /下一步做什么/ }).click()
  const drawer = page.getByRole('dialog', { name: '下一步做什么' })
  await expect(drawer.getByRole('button', { name: /定位数据库/ })).toBeVisible()
  await expect(drawer.getByRole('button', { name: /定位写提纲/ })).toHaveCount(0)
  await page.screenshot({ path: `../output/playwright/next-actions-${testInfo.project.name}.png` })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy()
  await drawer.getByRole('button', { name: /定位数据库/ }).click()
  await expect(drawer).toHaveCount(0)
  await expect(page.locator('.tree-card')).toHaveCount(5)
  await expect(page.locator('.tree-card.is-located')).toContainText('数据库')

  await page.getByRole('button', { name: '完成数据库' }).click()
  await page.getByRole('button', { name: /下一步做什么/ }).click()
  await expect(drawer.getByRole('button', { name: /定位数据库/ })).toHaveCount(0)
  await expect(drawer.getByRole('button', { name: /定位Spring Boot/ })).toBeVisible()
  await drawer.press('Escape')
  await expect(drawer).toHaveCount(0)
  await expect(page.getByRole('button', { name: /下一步做什么/ })).toBeFocused()

  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '写一本书', exact: true }).click()
  await page.getByRole('button', { name: /下一步做什么/ }).click()
  await expect(drawer.getByRole('button', { name: /定位写提纲/ })).toBeVisible()
  await expect(drawer.getByRole('button', { name: /定位Spring Boot/ })).toHaveCount(0)
  await drawer.getByRole('button', { name: '关闭下一步抽屉' }).click()
  await page.getByRole('button', { name: '完成写提纲' }).click()
  await page.getByRole('button', { name: /下一步做什么/ }).click()
  await expect(drawer.getByText('当前没有待完成的末级目标')).toBeVisible()
})

test('collapses a branch and restores it independently across reloads and main goals', async ({ page }, testInfo) => {
  const trees = [...initial, { ...base, id: 'other-root', title: '写一本书', status: 'ACTIVE' }]
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: trees }))
  await page.goto('/goals')
  await expect(page.getByRole('complementary', { name: '主目标侧边栏' })).toBeVisible()
  await page.getByRole('button', { name: '成为全栈工程师', exact: true }).click()
  await expect(page.locator('.tree-card')).toHaveCount(5)

  const collapse = page.getByRole('button', { name: '收起学习后端的2个子目标' })
  await collapse.focus()
  await collapse.press('Enter')
  await expect(page.locator('.tree-card')).toHaveCount(3)
  await expect(page.getByRole('link', { name: '查看Spring Boot详情' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '展开学习后端的2个子目标' })).toHaveAttribute('aria-expanded', 'false')

  await page.reload()
  await expect(page.locator('.tree-card')).toHaveCount(3)
  await closeSidebarOnMobile(page, testInfo)
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '写一本书', exact: true }).click()
  await expect(page.locator('.tree-card')).toHaveCount(1)
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '成为全栈工程师', exact: true }).click()
  await expect(page.locator('.tree-card')).toHaveCount(3)
  await page.getByRole('button', { name: '展开学习后端的2个子目标' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(5)
})

test('search locates a goal across main trees and opens its collapsed ancestors', async ({ page }, testInfo) => {
  const trees = [...initial, { ...base, id: 'other-root', title: '写一本书', status: 'ACTIVE' }]
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: trees }))
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await expect(page.locator('.tree-card')).toHaveCount(5)
  await page.getByRole('button', { name: '收起学习后端的2个子目标' }).click()
  await expect(page.getByRole('link', { name: '查看Spring Boot详情' })).toHaveCount(0)

  const search = page.getByRole('searchbox', { name: '搜索目标' })
  await search.fill('spring')
  await expect(page.getByRole('button', { name: /定位Spring Boot/ })).toBeVisible()
  await search.press('Enter')
  const match = page.locator('.tree-card.is-located')
  await expect(match.getByRole('link', { name: '查看Spring Boot详情' })).toBeFocused()
  await expect(match).toBeInViewport()
  await expect(page.locator('.tree-card')).toHaveCount(5)

  await search.fill('写一本书')
  await page.getByRole('button', { name: /定位写一本书/ }).click()
  await expect(page.locator('.tree-card')).toHaveCount(1)
  await expect(page.locator('.tree-card.is-located')).toContainText('写一本书')
  await expect(page.locator('.current-main-goal')).toHaveText('写一本书')
  await search.fill('没有这个目标')
  await expect(page.getByText('没有找到匹配的目标')).toBeVisible()
  await search.press('Escape')
  await expect(page.getByText('没有找到匹配的目标')).toHaveCount(0)
})

test('opens goal details from the card and manages persistent links without notes', async ({ page }, testInfo) => {
  const goals = [...initial, { ...base, id: 'other-root', title: '写一本书', status: 'ACTIVE' }]
  let links: { id: string, name: string, url: string }[] = []
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const match = url.pathname.match(/^\/api\/goals\/([^/]+)\/links(?:\/([^/]+))?$/)
    if (url.pathname === '/api/goals' && request.method() === 'GET') return route.fulfill({ json: goals })
    if (match) {
      if (request.method() === 'GET') return route.fulfill({ json: links })
      if (request.method() === 'POST') {
        const body = request.postDataJSON()
        links = [...links, { id: 'link-1', ...body }]
        return route.fulfill({ status: 201, json: links[links.length - 1] })
      }
      if (request.method() === 'PATCH') {
        links = links.map((link) => link.id === match[2] ? { ...link, ...request.postDataJSON() } : link)
        return route.fulfill({ json: links.find((link) => link.id === match[2]) })
      }
      if (request.method() === 'DELETE') {
        links = links.filter((link) => link.id !== match[2])
        return route.fulfill({ status: 204 })
      }
    }
    return route.continue()
  })
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await page.locator('.tree-card').filter({ hasText: '学习后端' }).first().locator('.tree-card-main').click()
  const dialog = page.getByRole('dialog', { name: '学习后端的目标详情' })
  await expect(dialog).toBeVisible()
  await expect(page).toHaveURL(/\/goals$/)
  await expect(dialog.getByText('还没有关联链接')).toBeVisible()
  await expect(dialog.getByText('备注')).toHaveCount(0)
  await dialog.getByRole('button', { name: '添加链接' }).click()
  await dialog.getByLabel('链接名称').fill('学习资料')
  await dialog.getByLabel('链接 URL').fill('javascript:alert(1)')
  await dialog.getByRole('button', { name: '保存链接' }).click()
  await expect(dialog.getByText('请输入有效的 http 或 https 链接')).toBeVisible()
  expect(links).toHaveLength(0)
  await dialog.getByLabel('链接 URL').fill('https://example.com/learn')
  await dialog.getByRole('button', { name: '保存链接' }).click()
  await expect(dialog.getByRole('link', { name: '学习资料' })).toHaveAttribute('target', '_blank')
  await expect(dialog.getByRole('link', { name: '学习资料' })).toHaveAttribute('rel', /noopener/)
  await page.screenshot({ path: `../output/playwright/goal-links-dialog-${testInfo.project.name}.png` })
  await dialog.getByRole('button', { name: '编辑学习资料' }).click()
  await dialog.getByLabel('链接名称').fill('课程页面')
  await dialog.getByRole('button', { name: '保存链接' }).click()
  await expect(dialog.getByRole('link', { name: '课程页面' })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭目标详情' }).click()
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '写一本书', exact: true }).click()
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '成为全栈工程师', exact: true }).click()
  await page.locator('.tree-card').filter({ hasText: '学习后端' }).first().locator('.tree-card-main').click()
  await expect(dialog.getByRole('link', { name: '课程页面' })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭目标详情' }).click()
  await page.reload()
  await closeSidebarOnMobile(page, testInfo)
  await page.locator('.tree-card').filter({ hasText: '学习后端' }).first().locator('.tree-card-main').click()
  await expect(page.getByRole('link', { name: '课程页面' })).toBeVisible()
  await page.getByRole('button', { name: '删除课程页面' }).click()
  await expect(page.getByText('还没有关联链接')).toBeVisible()
  await page.locator('.sheet-overlay').last().click({ position: { x: 5, y: 5 } })
  await expect(dialog).toHaveCount(0)
  await page.getByRole('button', { name: '为学习后端添加子目标' }).click()
  await expect(page.getByRole('dialog', { name: '学习后端的目标详情' })).toHaveCount(0)
})

test('moves roots and child goals within their siblings and keeps order after reload', async ({ page }) => {
  let goals = [
    { ...base, id: 'root-a', title: '项目甲', status: 'ACTIVE', sortOrder: 0 },
    { ...base, id: 'root-b', title: '项目乙', status: 'ACTIVE', sortOrder: 1 },
    { ...base, id: 'child-a', parentId: 'root-a', title: '学习前端', status: 'ACTIVE', sortOrder: 0 },
    { ...base, id: 'child-b', parentId: 'root-a', title: '学习后端', status: 'ACTIVE', sortOrder: 1 },
  ]
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && url.pathname === '/api/goals') return route.fulfill({ json: goals })
    if (request.method() === 'GET' && url.pathname.endsWith('/links')) return route.fulfill({ json: [] })
    const match = url.pathname.match(/^\/api\/goals\/([^/]+)\/move$/)
    if (request.method() === 'POST' && match) {
      const moving = goals.find((goal) => goal.id === match[1])!
      const siblings = goals.filter((goal) => (goal.parentId ?? null) === (moving.parentId ?? null))
        .sort((a, b) => a.sortOrder - b.sortOrder)
      const index = siblings.findIndex((goal) => goal.id === moving.id)
      const target = siblings[index + (request.postDataJSON().direction === 'UP' ? -1 : 1)]!
      goals = goals.map((goal) => goal.id === moving.id ? { ...goal, sortOrder: target.sortOrder, version: goal.version + 1 }
        : goal.id === target.id ? { ...goal, sortOrder: moving.sortOrder, version: goal.version + 1 } : goal)
      return route.fulfill({ status: 204 })
    }
    return route.continue()
  })
  await page.goto('/goals')
  await page.getByRole('button', { name: '上移主目标项目乙' }).click()
  await expect(page.locator('.main-goal-select').first()).toContainText('项目乙')
  await page.getByRole('button', { name: '项目甲', exact: true }).click()
  await page.locator('.tree-card').filter({ hasText: '学习后端' }).locator('.tree-card-main').click()
  const dialog = page.getByRole('dialog', { name: '学习后端的目标详情' })
  await dialog.getByRole('button', { name: '上移学习后端' }).click()
  await expect(dialog.getByRole('button', { name: '上移学习后端' })).toBeDisabled()
  const order = async () => page.locator('.tree-card').filter({ hasText: '学习' }).evaluateAll((cards) => cards.map((card) => ({
    title: card.textContent?.includes('学习后端') ? '后端' : '前端',
    top: Number.parseFloat((card as HTMLElement).style.top),
  })).sort((a, b) => a.top - b.top).map((card) => card.title))
  expect(await order()).toEqual(['后端', '前端'])
  await page.reload()
  await expect(page.locator('.main-goal-select').first()).toContainText('项目乙')
  expect(await order()).toEqual(['后端', '前端'])
})

test('creates, edits, reparents and deletes through the existing editor', async ({ page }, testInfo) => {
  let goals = initial.map((goal) => ({ ...goal }))
  let createdParentId: unknown = 'not-created'
  let createdPayload: Record<string, unknown> | null = null
  let updatedPayload: Record<string, unknown> | null = null
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const id = url.pathname.split('/')[3]
    if (request.method() === 'GET' && url.pathname === '/api/goals') {
      await route.fulfill({ json: goals })
    } else if (request.method() === 'GET' && id) {
      await route.fulfill({ json: { ...goals.find((goal) => goal.id === id), description: '', childGoals: [], completionCriteria: [], milestones: [] } })
    } else if (request.method() === 'POST' && url.pathname === '/api/goals') {
      const body = request.postDataJSON()
      createdPayload = body
      createdParentId = body.parentId
      const created = { ...base, ...body, id: 'new-task', status: 'ACTIVE' }
      goals.push(created)
      await route.fulfill({ json: created })
    } else if (request.method() === 'PATCH' && id) {
      const body = request.postDataJSON()
      updatedPayload = body
      goals = goals.map((goal) => goal.id === id ? { ...goal, ...body, version: goal.version + 1 } : goal)
      await route.fulfill({ json: { ...goals.find((goal) => goal.id === id), childGoals: [], completionCriteria: [], milestones: [] } })
    } else if (request.method() === 'DELETE' && id) {
      goals = goals.filter((goal) => goal.id !== id)
      await route.fulfill({ status: 204 })
    } else await route.continue()
  })
  page.on('dialog', (dialog) => dialog.accept())
  await page.goto('/goals')
  await closeSidebarOnMobile(page, testInfo)
  await page.getByRole('button', { name: '为学习后端添加子目标' }).click()
  await expect(page.getByRole('dialog', { name: '添加子目标' })).toBeVisible()
  await expect(page.getByLabel(/放在哪个目标下面/)).toHaveCount(0)
  await page.getByLabel('目标名称').fill('其他后端技术')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(6)
  expect(createdParentId).toBe('backend')
  expect(createdPayload).not.toHaveProperty('targetDate')
  expect(createdPayload).not.toHaveProperty('startDate')

  await page.getByRole('button', { name: '编辑Spring Boot' }).click()
  await expect(page.getByRole('dialog', { name: '编辑目标' })).toBeVisible()
  await page.getByLabel('目标名称').fill('Spring Boot 基础')
  await page.getByLabel(/放在哪个目标下面/).selectOption('root')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('link', { name: '查看Spring Boot 基础详情' })).toBeVisible()
  expect(updatedPayload).not.toHaveProperty('targetDate')

  await page.getByRole('button', { name: '删除其他后端技术' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(5)
})

test('empty tree keeps the create action and removes unused chrome', async ({ page }) => {
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: [] }))
  await page.goto('/goals')
  await expect(page.getByRole('button', { name: /下一步做什么/ })).toBeDisabled()
  await expect(page.getByText('还没有主目标').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '新建主目标' })).toBeVisible()
  await expect(page.locator('.app-topbar, .desktop-rail, .mobile-tabbar')).toHaveCount(0)
})

test('large tree opens at a readable root and supports multiple roots', async ({ page }) => {
  const many = [
    { ...base, id: 'root', title: '成为全栈工程师', status: 'ACTIVE' },
    ...Array.from({ length: 24 }, (_, index) => ({
      ...base, id: `leaf-${index}`, parentId: 'root', title: `第 ${index + 1} 个学习目标`, status: 'ACTIVE',
    })),
    { ...base, id: 'other-root', title: '另一个主目标', status: 'ACTIVE' },
  ]
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: many }))
  await page.goto('/goals')
  await page.getByRole('button', { name: '成为全栈工程师', exact: true }).click()
  await expect(page.locator('.tree-card')).toHaveCount(25)
  const scale = await page.locator('.tree-world').evaluate((el) => Number((el as HTMLElement).style.transform.match(/scale\(([^)]+)\)/)?.[1]))
  expect(scale).toBeGreaterThanOrEqual(.72)
  const root = page.getByRole('link', { name: '查看成为全栈工程师详情' })
  await expect(root).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy()
  await page.getByRole('button', { name: '重置视图' }).click()
  await expect(root).toBeInViewport()
})

test('switches independent trees and restores each view after a reload', async ({ page }, testInfo) => {
  const trees = [...initial, { ...base, id: 'other-root', title: '写一本书', status: 'ACTIVE' }]
  await page.route(/\/api\/goals(?:\?|$)/, (route) => route.fulfill({ json: trees }))
  await page.goto('/goals')
  const selectRoot = async (name: string) => {
    await openSidebarIfHidden(page)
    await page.getByRole('button', { name, exact: true }).click()
  }
  await expect(page.getByRole('complementary', { name: '主目标侧边栏' })).toBeVisible()
  await page.screenshot({ path: `../output/playwright/main-goal-sidebar-${testInfo.project.name}.png`, fullPage: true })
  await selectRoot('成为全栈工程师')
  await expect(page.locator('.tree-card')).toHaveCount(5)
  await page.getByRole('button', { name: '重置视图' }).click()
  const firstView = await page.locator('.tree-world').evaluate((el) => (el as HTMLElement).style.transform)
  const savedPosition = await page.evaluate(() => JSON.parse(localStorage.getItem('goalflow:goal-tree-views:v2') ?? '{}').root)
  await selectRoot('写一本书')
  await expect(page.locator('.tree-card')).toHaveCount(1)
  await selectRoot('成为全栈工程师')
  await expect(page.locator('.tree-world')).toHaveAttribute('style', new RegExp(firstView.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  await page.reload()
  await expect(page.locator('.tree-card')).toHaveCount(5)
  const restoredPosition = await page.evaluate(() => JSON.parse(localStorage.getItem('goalflow:goal-tree-views:v2') ?? '{}').root)
  expect(restoredPosition.scale).toBe(savedPosition.scale)
  expect(restoredPosition.centerX).toBeCloseTo(savedPosition.centerX)
  expect(restoredPosition.centerY).toBeCloseTo(savedPosition.centerY)
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: '隐藏主目标侧边栏' }).last().click()
  await expect(page.getByRole('complementary', { name: '主目标侧边栏' })).toHaveCount(0)
  await page.getByRole('button', { name: '展开主目标侧边栏' }).click()
  await expect(page.getByRole('complementary', { name: '主目标侧边栏' })).toBeVisible()
})

test('creates a root and confirms deleting its entire tree', async ({ page }) => {
  let goals = [...initial]
  let deleteCount = 0
  let createdParentId: unknown = 'not-created'
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && url.pathname === '/api/goals') {
      await route.fulfill({ json: goals })
    } else if (request.method() === 'POST' && url.pathname === '/api/goals/roots') {
      const body = request.postDataJSON()
      createdParentId = body.parentId
      const created = { ...base, ...body, id: 'new-root', status: 'ACTIVE' }
      goals = [...goals, created]
      await route.fulfill({ json: created })
    } else if (request.method() === 'DELETE' && url.pathname === '/api/goals/root/tree') {
      deleteCount++
      goals = goals.filter((goal) => goal.id !== 'root' && goal.parentId !== 'root' && goal.id !== 'spring' && goal.id !== 'database')
      await route.fulfill({ status: 204 })
    } else await route.continue()
  })
  await page.goto('/goals')
  await page.getByRole('button', { name: '新建主目标' }).click()
  await expect(page.getByRole('dialog', { name: '新建主目标' })).toBeVisible()
  await expect(page.getByLabel(/放在哪个目标下面/)).toHaveCount(0)
  await page.getByLabel('目标名称').fill('新的主目标')
  await page.getByRole('button', { name: '保存' }).click()
  expect(createdParentId).toBeUndefined()
  await expect(page.locator('.tree-card')).toHaveCount(1)
  page.once('dialog', (dialog) => {
    expect(dialog.message()).toContain('全部 4 个子目标')
    void dialog.dismiss()
  })
  await page.getByRole('button', { name: '删除主目标成为全栈工程师及其目标树' }).click()
  expect(deleteCount).toBe(0)
  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: '删除主目标成为全栈工程师及其目标树' }).click()
  await expect(page.getByRole('button', { name: '成为全栈工程师', exact: true })).toHaveCount(0)
  expect(deleteCount).toBe(1)
})

test('undoes deletion and restores a deleted tree from the recycle bin after reload', async ({ page }, testInfo) => {
  let goals = initial.map((goal) => ({ ...goal }))
  let trash: Array<{ id: string, title: string, deletedAt: string, descendantCount: number, version: number, canRestore: boolean }> = []
  let restoreCount = 0
  const restoredVersions: string[] = []
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && url.pathname === '/api/goals') {
      await route.fulfill({ json: goals })
    } else if (request.method() === 'GET' && url.pathname === '/api/goals/trash') {
      await route.fulfill({ json: trash })
    } else if (request.method() === 'DELETE' && url.pathname === '/api/goals/root/tree') {
      goals = []
      trash = [{ id: 'root', title: '成为全栈工程师', deletedAt: new Date().toISOString(), descendantCount: 4, version: 2, canRestore: true }]
      await route.fulfill({ status: 204 })
    } else if (request.method() === 'POST' && url.pathname === '/api/goals/root/restore') {
      restoredVersions.push(url.searchParams.get('version') ?? '')
      restoreCount++
      goals = initial.map((goal) => ({ ...goal }))
      trash = []
      await route.fulfill({ status: 204 })
    } else await route.continue()
  })
  page.on('dialog', (dialog) => void dialog.accept())
  await page.goto('/goals')
  await page.getByRole('button', { name: '删除主目标成为全栈工程师及其目标树' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(0)
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await expect(page.locator('.tree-card')).toHaveCount(5)
  expect(restoreCount).toBe(1)

  await page.getByRole('button', { name: '删除主目标成为全栈工程师及其目标树' }).click()
  await page.reload()
  await openTrashForViewport(page, testInfo)
  const drawer = page.getByRole('dialog', { name: '回收站' })
  await expect(drawer).toContainText('包含 4 个子目标')
  await drawer.getByRole('button', { name: '恢复成为全栈工程师' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(5)
  expect(restoreCount).toBe(2)
  expect(restoredVersions).toEqual(['2', '2'])
  await expect(drawer).toContainText('回收站是空的')
})

test('keeps parent choices inside one tree, deletes a branch and shows archived roots', async ({ page }) => {
  let goals = [...initial, { ...base, id: 'archived-root', title: '旧项目', status: 'ARCHIVED' }]
  let deletedBranch = false
  await page.route(/\/api\/goals(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const id = url.pathname.split('/')[3]
    if (request.method() === 'GET' && url.pathname === '/api/goals') {
      await route.fulfill({ json: goals })
    } else if (request.method() === 'GET' && id) {
      await route.fulfill({ json: { ...goals.find((goal) => goal.id === id), description: '', childGoals: [], completionCriteria: [], milestones: [] } })
    } else if (request.method() === 'DELETE' && url.pathname === '/api/goals/backend/tree') {
      deletedBranch = true
      goals = goals.filter((goal) => !['backend', 'spring', 'database'].includes(goal.id))
      await route.fulfill({ status: 204 })
    } else await route.continue()
  })
  await page.goto('/goals')
  await expect(page.getByText('已归档')).toBeVisible()
  await page.getByRole('button', { name: '成为全栈工程师', exact: true }).click()
  await page.getByRole('button', { name: '编辑Spring Boot' }).click()
  await expect(page.getByRole('option', { name: '旧项目' })).toHaveCount(0)
  await expect(page.getByRole('option', { name: '作为主目标' })).toHaveCount(0)
  await page.getByRole('button', { name: '关闭目标编辑器' }).click()
  page.once('dialog', (dialog) => {
    expect(dialog.message()).toContain('全部 2 个子目标')
    void dialog.accept()
  })
  await page.getByRole('button', { name: '删除学习后端' }).click()
  await expect(page.locator('.tree-card')).toHaveCount(2)
  expect(deletedBranch).toBe(true)
  await openSidebarIfHidden(page)
  await page.getByRole('button', { name: /旧项目.*已归档/ }).click()
  await expect(page.getByRole('button', { name: '取消归档旧项目' })).toBeVisible()
  await expect(page.locator('.tree-card')).toHaveCount(1)
})

test('failed tree deletion keeps the selection and saved canvas view', async ({ page }) => {
  await page.route(/\/api\/goals(?:\/|\?|$)/, (route) => {
    const request = route.request()
    if (request.method() === 'DELETE') {
      return route.fulfill({ status: 409, json: { code: 'VERSION_CONFLICT', message: '内容已更新，请刷新后再提交。' } })
    }
    return route.fulfill({ json: initial })
  })
  page.on('dialog', (dialog) => void dialog.accept())
  await page.goto('/goals')
  await expect(page.locator('.tree-card')).toHaveCount(5)
  const view = await page.evaluate(() => localStorage.getItem('goalflow:goal-tree-views:v2'))
  await page.getByRole('button', { name: '删除主目标成为全栈工程师及其目标树' }).click()
  await expect(page.getByRole('alert')).toContainText('内容已更新')
  await expect(page.locator('.tree-card')).toHaveCount(5)
  await expect(page.getByRole('button', { name: '成为全栈工程师', exact: true })).toHaveAttribute('aria-current', 'true')
  expect(await page.evaluate(() => localStorage.getItem('goalflow:goal-tree-views:v2'))).toBe(view)
})
