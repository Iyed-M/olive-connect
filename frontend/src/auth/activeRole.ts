import type { Role } from './types'

// The Active role choice (role switcher) is UI only and kept in localStorage.
// Every access is wrapped in try/catch: storage can be blocked or full.
const KEY = 'oliveconnect.activeRole'

const listeners = new Set<() => void>()

function read(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

// Held in memory too, so it still works when storage is blocked and React can subscribe to it.
let stored = read()

export const getStoredRole = () => stored

export function subscribeStoredRole(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// `null` clears the choice (used on logout).
export function setStoredRole(role: Role | null) {
  stored = role
  try {
    if (role) localStorage.setItem(KEY, role)
    else localStorage.removeItem(KEY)
  } catch {
    // ignore: the choice then only lasts until the next reload
  }
  listeners.forEach((l) => l())
}
