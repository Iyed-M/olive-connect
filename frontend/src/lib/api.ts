import { refresh } from '@/auth/refresh'
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
  retryAfterSeconds?: number

  constructor(
    status: number,
    code: string,
    detail: string,
    errors: FieldError[] = [],
    retryAfterSeconds?: number,
  ) {
    super(detail)
    this.status = status
    this.code = code
    this.detail = detail
    this.errors = errors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

function send(path: string, init?: RequestInit) {
  const token = getAccessToken()
  return fetch(`/api${path}`, {
    ...init,
    credentials: 'same-origin', // sends the refresh cookie to /api/auth/*
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
}

// Tiny fetch wrapper reused by every milestone. Throws an ApiError on non-2xx responses.
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await send(path, init)
  // Expired access token: refresh once (shared by concurrent calls), then retry the call once.
  // /auth/* calls are excluded: a 401 there (bad credentials) is a real answer, not an expiry.
  if (res.status === 401 && getAccessToken() && !path.startsWith('/auth/')) {
    if (await refresh()) res = await send(path, init)
  }
  if (!res.ok) {
    // The body may not be JSON (proxy error page, API down): fall back to the HTTP status text.
    const problem = await res.json().catch(() => null)
    throw new ApiError(
      res.status,
      problem?.code ?? 'UNKNOWN',
      problem?.detail ?? res.statusText,
      problem?.errors ?? [],
      problem?.retryAfterSeconds,
    )
  }
  if (res.status === 204) return undefined as T
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
