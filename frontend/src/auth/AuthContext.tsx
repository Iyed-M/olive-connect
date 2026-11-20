import { createContext, useCallback, useContext, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { getStoredRole, setStoredRole, subscribeStoredRole } from './activeRole'
import { activeRole } from './roles'
import { getUser, setSession, subscribe } from './session'
import type { LoginResponse, Me, Role } from './types'

export interface AuthContextValue {
  user: Me | null
  // The role chosen in the switcher (UI only), or the priority role when none is valid.
  activeRole: Role | null
  setActiveRole: (role: Role) => void
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
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

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      activeRole: user ? activeRole(user.roles, stored) : null,
      setActiveRole: setStoredRole,
      login,
      logout,
      isAuthenticated: () => getUser() !== null,
    }),
    [user, stored, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
