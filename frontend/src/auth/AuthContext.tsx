import { createContext, useCallback, useContext, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { getStoredRole, setStoredRole, subscribeStoredRole } from './activeRole'
import { rememberOnboardingDone } from './onboarding'
import { activeRole } from './roles'
import { getAccessToken, getUser, setSession, subscribe } from './session'
import type { LoginResponse, Me, Role } from './types'

export interface AuthContextValue {
  user: Me | null
  // The role chosen in the switcher (UI only), or the priority role when none is valid.
  activeRole: Role | null
  setActiveRole: (role: Role) => void
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  completeOnboarding: () => Promise<void>
  // Reads the session directly (not React state), so router guards are never one render late.
  isAuthenticated: () => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // session.ts is the source of truth; this re-renders when it changes (login, refresh, logout).
  const user = useSyncExternalStore(subscribe, getUser)
  const stored = useSyncExternalStore(subscribeStoredRole, getStoredRole)

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setSession(res.accessToken, res.user)
    return res.user
  }, [])

  const logout = useCallback(async () => {
    // Ends the session on the server too; if that fails we still log out locally.
    await api('/auth/logout', { method: 'POST' }).catch(() => undefined)
    setSession(null, null)
    setStoredRole(null)
  }, [])

  const completeOnboarding = useCallback(async () => {
    const current = getUser()
    if (!current || current.onboardingDone) return

    // Update the in-memory session immediately so the tour never reopens in this tab. The API
    // call keeps the server-side onboarding flag authoritative when the deployed API supports it.
    rememberOnboardingDone(current.id)
    setSession(getAccessToken(), { ...current, onboardingDone: true })
    await api('/me', {
      method: 'PATCH',
      body: JSON.stringify({ onboardingDone: true }),
    }).catch(() => undefined)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      activeRole: user ? activeRole(user.roles, stored) : null,
      setActiveRole: setStoredRole,
      login,
      logout,
      completeOnboarding,
      isAuthenticated: () => getUser() !== null,
    }),
    [completeOnboarding, user, stored, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
