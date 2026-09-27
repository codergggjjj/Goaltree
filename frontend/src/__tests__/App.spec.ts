import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'

describe('App', () => {
  it('renders the product navigation', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/dashboard', component: { template: '<div>Dashboard</div>' } }],
    })
    await router.push('/dashboard')
    await router.isReady()

    const wrapper = mount(AppShell, {
      global: { plugins: [router] },
      slots: { default: '<div>Dashboard</div>' },
    })

    expect(wrapper.text()).toContain('Goaltree')
    expect(wrapper.text()).toContain('目标')
    expect(wrapper.text()).not.toContain('复盘')
    expect(wrapper.findAll('.rail-nav .rail-link')).toHaveLength(1)
  })

  it('gives the goals route the full canvas without removing detail navigation', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/goals', name: 'goals', component: { template: '<div>Goals</div>' } },
        { path: '/goals/:id', name: 'goal-detail', component: { template: '<div>Detail</div>' } },
      ],
    })
    await router.push('/goals')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    expect(wrapper.classes()).toContain('is-goals-canvas')
    expect(wrapper.find('.desktop-rail').exists()).toBe(false)
    expect(wrapper.find('.app-topbar').exists()).toBe(false)

    await router.push('/goals/example')
    expect(wrapper.find('.desktop-rail').exists()).toBe(true)
    expect(wrapper.find('.app-topbar').exists()).toBe(true)
  })
})
