import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { getUser, setSession } from './session'
import type { LoginResponse, Me } from './types'

export interface AuthContextValue {
  user: Me | null
  login: (email: string, password: string) => Promise<Me>
  logout: () => void
  // Reads the session directly (not React state), so router guards are never one render late.
  isAuthenticated: () => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // The state only triggers re-renders; the source of truth is session.ts.
  const [user, setUser] = useState<Me | null>(getUser())

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setSession(res.accessToken, res.user)
    setUser(res.user)
    return res.user
  }, [])

  const logout = useCallback(() => {
    setSession(null, null)
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, login, logout, isAuthenticated: () => getUser() !== null }),
    [user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
