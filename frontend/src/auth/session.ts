import type { Me } from './types'
import { restoreOnboardingState } from './onboarding'

// The session lives in memory only (never localStorage): a reload starts logged out, and the
// refresh cookie restores it (see refresh.ts). A module variable is used so that api.ts and the
// router guards always read the current value, even before React has re-rendered.
let accessToken: string | null = null
let user: Me | null = null
const listeners = new Set<() => void>()

export const getAccessToken = () => accessToken
export const getUser = () => user

// Lets React follow the session when it changes outside a component (silent refresh).
export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function setSession(token: string | null, me: Me | null) {
  accessToken = token
  user = me ? restoreOnboardingState(me) : null
  listeners.forEach((l) => l())
}
