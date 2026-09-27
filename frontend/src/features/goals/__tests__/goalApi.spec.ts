import { describe, expect, it } from 'vitest'
import { getApiProblem } from '../goalApi'

describe('goal API errors', () => {
  it('turns a plain text access error into a readable problem', () => {
    const problem = getApiProblem({
      isAxiosError: true,
      response: { status: 403, data: 'Invalid CORS request' },
    })

    expect(problem).toEqual({
      code: 'ACCESS_DENIED',
      message: '请求被服务拒绝，请确认前后端地址配置后重试。',
    })
  })
})
