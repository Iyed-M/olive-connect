import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { CircleHelp, Leaf, LogOut, UserCircle, X } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { NAV } from '@/auth/nav'
import { ROLE_HOME, ROLE_LABEL } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { Button } from '@/components/ui/button'
import { GuidedTour } from '@/components/GuidedTour'
import { HelpDrawer } from '@/components/HelpDrawer'
import { NotificationBell } from '@/features/notifications/NotificationBell'
import { useEventStream } from '@/features/events/eventStream'
import { shouldStartTour, tourStepsForRole } from '@/tours'

// The layout of every role: header, side nav (from md), bottom nav (below md).
export function AppLayout() {
  const { user, activeRole, setActiveRole, logout, completeOnboarding } = useAuth()
  const navigate = useNavigate()
  const href = useLocation({ select: (l) => l.href })
  const pathname = useLocation({ select: (l) => l.pathname })
  const [loggingOut, setLoggingOut] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [tourOpen, setTourOpen] = useState(false)
  const startedTourFor = useRef<string | null>(null)
  const eventStream = useEventStream(Boolean(user))
  const userId = user?.id
  const onboardingDone = user?.onboardingDone

  useEffect(() => {
    // A temporary-password user must be able to complete that form before the tour can
    // cover the authenticated layout. The pathname dependency starts it after the redirect.
    if (pathname === '/change-password' || !userId || !activeRole || !shouldStartTour(onboardingDone ?? true)) return
    const tourKey = `${userId}:${activeRole}`
    if (startedTourFor.current === tourKey) return

    startedTourFor.current = tourKey
    const timer = window.setTimeout(() => setTourOpen(true), 250)
    return () => window.clearTimeout(timer)
  }, [activeRole, onboardingDone, pathname, userId])

  const finishTour = useCallback(() => {
    setTourOpen(false)
    void completeOnboarding()
  }, [completeOnboarding])

  // The session ended without a click on "Se déconnecter" (refresh failed): the guard only runs on
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
    setProfileOpen(false)
    setLoggingOut(true)
    await logout()
    await navigate({ to: '/login' })
  }

  function openHelp() {
    setProfileOpen(false)
    setHelpOpen(true)
  }

  function replayTour() {
    setProfileOpen(false)
    setTourOpen(true)
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 flex h-14 items-center justify-between gap-2 border-b bg-background px-2 sm:px-4">
        <span className="flex min-w-0 items-center gap-2 font-heading text-lg font-bold text-primary">
          <Leaf className="size-5 shrink-0" aria-hidden />
          <span className="hidden truncate sm:inline">OliveConnect</span>
          <span className="sr-only">OliveConnect</span>
        </span>
        <div className="flex min-w-0 items-center gap-1 sm:gap-2">
          <NotificationBell sseActive={eventStream.active} />
          <Button type="button" variant="outline" size="icon" onClick={openHelp} aria-label="Ouvrir l’aide contextuelle" data-tour="context-help">
            <CircleHelp aria-hidden />
          </Button>
          {user.roles.length >= 2 && (
            <select
              aria-label="Rôle actif"
              value={activeRole}
              onChange={(e) => onSwitch(e.target.value as Role)}
              className="h-11 max-w-28 truncate rounded-md border bg-background px-2 text-sm sm:max-w-40"
              data-tour="active-role"
            >
              {user.roles.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABEL[role]}
                </option>
              ))}
            </select>
          )}
          <div className="relative" data-tour="profile-menu">
            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label="Ouvrir le menu du profil"
              aria-haspopup="menu"
              aria-expanded={profileOpen}
              onClick={() => setProfileOpen((open) => !open)}
            >
              <UserCircle aria-hidden />
            </Button>
            {profileOpen && (
              <div className="absolute right-0 top-full z-30 mt-2 flex w-64 max-w-[calc(100vw-1rem)] flex-col gap-2 rounded-xl border bg-background p-3 shadow-xl" role="menu" aria-label="Menu du profil">
                <div className="flex items-start justify-between gap-2 border-b pb-2">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{user.firstName} {user.lastName}</p>
                    <p className="truncate text-xs text-muted-foreground">{user.email}</p>
                  </div>
                  <Button type="button" variant="ghost" size="icon-xs" aria-label="Fermer le menu du profil" onClick={() => setProfileOpen(false)}>
                    <X aria-hidden />
                  </Button>
                </div>
                <Button type="button" role="menuitem" variant="ghost" className="w-full justify-start" onClick={replayTour}>
                  Revoir la visite guidée
                </Button>
                <Button type="button" role="menuitem" variant="ghost" className="w-full justify-start" onClick={() => void onLogout()}>
                  <LogOut aria-hidden />
                  Se déconnecter
                </Button>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        <nav aria-label="Principal" className="hidden w-56 shrink-0 flex-col gap-1 border-r p-3 md:flex" data-tour="role-navigation">
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
        <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-6 pb-24 md:pb-8" data-tour="main-content">
          <Outlet />
        </main>
      </div>

      <nav
        aria-label="Principal"
        className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
        data-tour="role-navigation"
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

      <HelpDrawer open={helpOpen} pathname={pathname} role={activeRole} onClose={() => setHelpOpen(false)} />
      {tourOpen && <GuidedTour open={tourOpen} steps={tourStepsForRole(activeRole)} onComplete={finishTour} />}
    </div>
  )
}
