import { onMounted, ref } from 'vue'
import { http } from './http'

export type BackendStatus = 'checking' | 'online' | 'offline'

type HealthResponse = {
  status: string
  application: string
  version: string
  checkedAt: string
}

export function useBackendHealth() {
  const status = ref<BackendStatus>('checking')
  const version = ref<string>()

  const check = async () => {
    status.value = 'checking'
    try {
      const response = await http.get<HealthResponse>('/api/system/health')
      status.value = response.data.status === 'UP' ? 'online' : 'offline'
      version.value = response.data.version
    } catch {
      status.value = 'offline'
      version.value = undefined
    }
  }

  onMounted(check)
  return { status, version, check }
}
