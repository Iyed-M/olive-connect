import type { ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import { useAuth } from '@/auth/AuthContext'
import { ROLE_HOME, ROLE_LABEL } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { buttonVariants } from '@/components/ui/button'

// Role guard of a page. It sits inside the layout, so a User without the role sees the
// "Not allowed" page with header and nav (not a redirect). The API checks roles again anyway.
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, activeRole } = useAuth()
  if (!user || !activeRole) return null // logging out
  if (user.roles.includes(role)) return children

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Not allowed</h1>
      <p className="text-muted-foreground">This page is for the role "{ROLE_LABEL[role]}".</p>
      <Link to={ROLE_HOME[activeRole]} className={buttonVariants({ className: 'w-fit' })}>
        Go to my home
      </Link>
    </>
  )
}
