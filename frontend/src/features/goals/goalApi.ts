import axios from 'axios'
import { http } from '@/services/http'
import type {
  ApiProblem,
  CreateGoalInput,
  GoalDetail,
  GoalListItem,
  GoalLink,
  GoalLinkInput,
  TrashGoalItem,
  UpdateGoalInput,
} from './types'

export const goalApi = {
  async list(): Promise<GoalListItem[]> {
    const response = await http.get<GoalListItem[]>('/api/goals', {
      params: { includeArchived: true },
    })
    return response.data
  },

  async get(goalId: string): Promise<GoalDetail> {
    const response = await http.get<GoalDetail>(`/api/goals/${goalId}`)
    return response.data
  },

  async listTrash(): Promise<TrashGoalItem[]> {
    const response = await http.get<TrashGoalItem[]>('/api/goals/trash')
    return response.data
  },

  async restore(goalId: string, version: number): Promise<void> {
    await http.post(`/api/goals/${goalId}/restore`, null, { params: { version } })
  },

  async listLinks(goalId: string): Promise<GoalLink[]> {
    const response = await http.get<GoalLink[]>(`/api/goals/${goalId}/links`)
    return response.data
  },

  async createLink(goalId: string, input: GoalLinkInput): Promise<GoalLink> {
    const response = await http.post<GoalLink>(`/api/goals/${goalId}/links`, input)
    return response.data
  },

  async updateLink(goalId: string, linkId: string, input: GoalLinkInput): Promise<GoalLink> {
    const response = await http.patch<GoalLink>(`/api/goals/${goalId}/links/${linkId}`, input)
    return response.data
  },

  async deleteLink(goalId: string, linkId: string): Promise<void> {
    await http.delete(`/api/goals/${goalId}/links/${linkId}`)
  },

  async createRoot(payload: CreateGoalInput): Promise<GoalDetail> {
    const response = await http.post<GoalDetail>('/api/goals/roots', payload)
    return response.data
  },

  async createChild(payload: CreateGoalInput): Promise<GoalDetail> {
    const response = await http.post<GoalDetail>('/api/goals', payload)
    return response.data
  },

  async update(goalId: string, payload: UpdateGoalInput): Promise<GoalDetail> {
    const response = await http.patch<GoalDetail>(`/api/goals/${goalId}`, payload)
    return response.data
  },

  async delete(goalId: string, version: number): Promise<void> {
    await http.delete(`/api/goals/${goalId}`, { params: { version } })
  },

  async deleteTree(goalId: string, version: number): Promise<void> {
    await http.delete(`/api/goals/${goalId}/tree`, { params: { version } })
  },

  async move(goalId: string, version: number, direction: 'UP' | 'DOWN'): Promise<void> {
    await http.post(`/api/goals/${goalId}/move`, { version, direction })
  },

  async action(
    goalId: string,
    action: 'start' | 'pause' | 'resume' | 'complete' | 'tree-complete' | 'archive',
    version: number,
    reason?: string,
  ): Promise<GoalDetail> {
    const response = await http.post<GoalDetail>(`/api/goals/${goalId}/${action}`, {
      version,
      reason,
    })
    return response.data
  },
}

export function getApiProblem(error: unknown): ApiProblem {
  if (axios.isAxiosError(error)) {
    const data: unknown = error.response?.data
    if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
      return data as ApiProblem
    }
    if (error.response?.status === 403) {
      return { code: 'ACCESS_DENIED', message: '请求被服务拒绝，请确认前后端地址配置后重试。' }
    }
    if (error.response) {
      return { code: 'REQUEST_FAILED', message: `保存失败（HTTP ${error.response.status}），请稍后重试。` }
    }
  }
  return { code: 'NETWORK_ERROR', message: '暂时无法连接服务，请稍后重试。' }
}
