import { setSession } from './session'
import type { LoginResponse } from './types'

interface RefreshResult {
  res: LoginResponse | null
  // 401 code SESSION_EXPIRED = a cookie was sent but refused (e.g. another tab just rotated it)
  retryable: boolean
}

// Plain fetch on purpose: api.ts imports this file, so it must not import api.ts back.
async function callRefresh(): Promise<RefreshResult> {
  const res = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
  if (res.ok) return { res: (await res.json()) as LoginResponse, retryable: false }
  const problem = await res.json().catch(() => null)
  return { res: null, retryable: problem?.code === 'SESSION_EXPIRED' }
}

let inFlight: Promise<boolean> | null = null

// Single-flight: callers that arrive while a refresh is running share its result.
// The cookie is rotated on every use, so two parallel refreshes would make the second one fail.
export function refresh(): Promise<boolean> {
  inFlight ??= doRefresh().finally(() => {
    inFlight = null
  })
  return inFlight
}

async function doRefresh(): Promise<boolean> {
  let result = await callRefresh()
  if (!result.res && result.retryable) {
    // Another tab may have just rotated the shared cookie: wait a moment and try once more.
    // Visitors without a cookie get UNAUTHENTICATED instead, so they are never delayed.
    await new Promise((resolve) => setTimeout(resolve, 1000))
    result = await callRefresh()
  }
  if (!result.res) {
    setSession(null, null)
    return false
  }
  setSession(result.res.accessToken, result.res.user)
  return true
}
