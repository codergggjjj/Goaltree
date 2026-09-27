import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import GoalEditorSheet from '../GoalEditorSheet.vue'

describe('GoalEditorSheet creation context', () => {
  it('requires a selected parent before a child can be saved', async () => {
    const wrapper = mount(GoalEditorSheet, {
      props: { open: false, goals: [], planningMode: 'child' },
      attachTo: document.body,
    })

    try {
      await wrapper.setProps({ open: true })
      const dialog = document.body.querySelector('[role="dialog"]')
      expect(dialog?.textContent).toContain('请先从一个目标节点选择“添加子目标”。')
      expect(dialog?.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true)
      expect(wrapper.emitted('create')).toBeUndefined()
    } finally {
      wrapper.unmount()
    }
  })
})
