import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/goals' },
    { path: '/dashboard', redirect: '/goals' },
    { path: '/goals', name: 'goals', component: () => import('@/views/GoalsView.vue') },
    {
      path: '/goals/:id',
      name: 'goal-detail',
      component: () => import('@/views/GoalDetailView.vue'),
    },
    { path: '/plans', redirect: '/goals' },
    { path: '/reviews', redirect: '/goals' },
    { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach(() => {
  requestAnimationFrame(() =>
    document.querySelector<HTMLElement>('#main-content')?.focus({ preventScroll: true }),
  )
})

export default router
