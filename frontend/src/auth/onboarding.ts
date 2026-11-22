import type { Me } from './types'

const KEY = 'oliveconnect.onboardingDone'

function readCompletedUserIds(): string[] {
  if (typeof localStorage === 'undefined') return []

  try {
    const value: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((id): id is string => typeof id === 'string') : []
  } catch {
    return []
  }
}

export function rememberOnboardingDone(userId: string): void {
  try {
    localStorage.setItem(KEY, JSON.stringify([...new Set([...readCompletedUserIds(), userId])]))
  } catch {
    // The in-memory session still prevents the tour from reopening in this tab.
  }
}

export function restoreOnboardingState(user: Me): Me {
  if (user.onboardingDone || !readCompletedUserIds().includes(user.id)) return user
  return { ...user, onboardingDone: true }
}
