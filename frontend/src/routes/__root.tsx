import { Outlet, createRootRouteWithContext } from '@tanstack/react-router'
import type { AuthContextValue } from '@/auth/AuthContext'

export interface RouterContext {
  auth: AuthContextValue
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: () => (
    <main className="mx-auto flex min-h-screen w-full max-w-xl flex-col gap-6 px-4 py-8 sm:py-14">
      <Outlet />
    </main>
  ),
})
