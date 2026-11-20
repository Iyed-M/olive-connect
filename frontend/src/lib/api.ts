import { getAccessToken } from '@/auth/session'

// Error body of the backend: RFC 7807 ProblemDetail plus our `code` and optional field errors.
export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  status: number
  code: string
  detail: string
  errors: FieldError[]

  constructor(status: number, code: string, detail: string, errors: FieldError[] = []) {
    super(detail)
    this.status = status
    this.code = code
    this.detail = detail
    this.errors = errors
  }
}

// Tiny fetch wrapper reused by every milestone. Throws an ApiError on non-2xx responses.
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getAccessToken()
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
  if (!res.ok) {
    // The body may not be JSON (proxy error page, API down): fall back to the HTTP status text.
    const problem = await res.json().catch(() => null)
    throw new ApiError(
      res.status,
      problem?.code ?? 'UNKNOWN',
      problem?.detail ?? res.statusText,
      problem?.errors ?? [],
    )
  }
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
