import { Outlet, createRootRouteWithContext } from '@tanstack/react-router'
import type { AuthContextValue } from '@/auth/AuthContext'

export interface RouterContext {
  auth: AuthContextValue
}

// No page shell here: public pages use <PublicPage>, the others use the _app layout.
export const Route = createRootRouteWithContext<RouterContext>()({ component: Outlet })
