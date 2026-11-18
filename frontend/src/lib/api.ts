// Tiny fetch wrapper reused by every milestone. Throws on non-2xx responses.
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json() as Promise<T>
}

export type Health = 'UP' | 'DOWN'

export interface SystemStatus {
  api: Health
  mongo: Health
  redis: Health
  version: string
}

export const getSystemStatus = () => api<SystemStatus>('/system/status')
