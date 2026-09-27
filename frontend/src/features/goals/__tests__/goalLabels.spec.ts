import { describe, expect, it } from 'vitest'
import { revisionChanges } from '../goalLabels'
import type { Revision } from '../types'

describe('goal revision labels', () => {
  it('describes an initial goal revision', () => {
    const revision: Revision = {
      id: 'revision-1',
      entityType: 'GOAL',
      entityId: 'goal-1',
      revisionNo: 1,
      action: 'CREATE',
      effectiveAt: '2026-07-01T00:00:00Z',
      after: { title: '完成作品', status: 'DRAFT' },
      createdAt: '2026-07-01T00:00:00Z',
    }

    expect(revisionChanges(revision)).toEqual(['建立目标初始版本'])
  })

  it('lists changed fields instead of hiding the diff', () => {
    const revision: Revision = {
      id: 'revision-2',
      entityType: 'GOAL',
      entityId: 'goal-1',
      revisionNo: 2,
      action: 'UPDATE',
      reason: '根据复盘调整周期',
      effectiveAt: '2026-08-01T00:00:00Z',
      before: { title: '完成作品', targetDate: '2026-09-01' },
      after: { title: '完成并发布作品', targetDate: '2026-10-01' },
      createdAt: '2026-08-01T00:00:00Z',
    }

    expect(revisionChanges(revision)).toEqual(['目标名称已更新', '目标日期已更新'])
  })
})
