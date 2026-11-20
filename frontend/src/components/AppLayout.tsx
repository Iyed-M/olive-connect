import { useState } from 'react'
import { Link, Navigate, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { Leaf, LogOut } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { NAV } from '@/auth/nav'
import { ROLE_HOME, ROLE_LABEL } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { Button } from '@/components/ui/button'
import { NotificationBell } from '@/features/notifications/NotificationBell'

// The layout of every role: header, side nav (from md), bottom nav (below md).
export function AppLayout() {
  const { user, activeRole, setActiveRole, logout } = useAuth()
  const navigate = useNavigate()
  const href = useLocation({ select: (l) => l.href })
  const [loggingOut, setLoggingOut] = useState(false)

  // The session ended without a click on "Log out" (refresh failed): the guard only runs on
  // navigation, so send the User to /login here, with ?redirect= to come back.
  // After an explicit logout we go to plain /login instead (onLogout), so render nothing meanwhile.
  if (!user || !activeRole) {
    return loggingOut ? null : <Navigate to="/login" search={{ redirect: href }} replace />
  }

  const items = NAV[activeRole]

  function onSwitch(role: Role) {
    setActiveRole(role)
    void navigate({ to: ROLE_HOME[role] })
  }

  async function onLogout() {
    setLoggingOut(true)
    await logout()
    await navigate({ to: '/login' })
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 flex h-14 items-center justify-between gap-2 border-b bg-background px-4">
        <span className="flex items-center gap-2 font-heading text-lg font-bold text-primary">
          <Leaf className="size-5" aria-hidden /> OliveConnect
        </span>
        <div className="flex items-center gap-2">
          <NotificationBell />
          {user.roles.length >= 2 && (
            <select
              aria-label="Active role"
              value={activeRole}
              onChange={(e) => onSwitch(e.target.value as Role)}
              className="h-11 max-w-40 rounded-md border bg-background px-2 text-sm"
            >
              {user.roles.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABEL[role]}
                </option>
              ))}
            </select>
          )}
          <Button variant="outline" onClick={onLogout} aria-label="Log out" className="h-11 min-w-11">
            <LogOut aria-hidden />
            <span className="hidden sm:inline">Log out</span>
          </Button>
        </div>
      </header>

      <div className="flex flex-1">
        <nav aria-label="Main" className="hidden w-56 shrink-0 flex-col gap-1 border-r p-3 md:flex">
          {items.map(({ to, label, icon: Icon }) => (
            <Link
              key={label}
              to={to}
              className="flex h-11 items-center gap-3 rounded-md px-3 text-sm font-medium hover:bg-muted"
              activeProps={{ className: 'bg-muted text-primary' }}
            >
              <Icon className="size-5" aria-hidden /> {label}
            </Link>
          ))}
        </nav>

        {/* pb-24: keeps the content clear of the fixed bottom nav on phones */}
        <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-6 pb-24 md:pb-8">
          <Outlet />
        </main>
      </div>

      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {items.map(({ to, label, icon: Icon }) => (
          <Link
            key={label}
            to={to}
            className="flex min-h-14 min-w-16 flex-1 flex-col items-center justify-center gap-0.5 text-xs font-medium text-muted-foreground"
            activeProps={{ className: 'text-primary' }}
          >
            <Icon className="size-5" aria-hidden /> {label}
          </Link>
        ))}
      </nav>
    </div>
  )
}
