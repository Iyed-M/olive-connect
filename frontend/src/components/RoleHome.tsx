import { RequireRole } from '@/components/RequireRole'
import type { Role } from '@/auth/types'

// Placeholder of a Role home, until the milestone that fills it.
export function RoleHome({ role, title, milestone }: { role: Role; title: string; milestone: string }) {
  return (
    <RequireRole role={role}>
      <h1 className="font-heading text-2xl font-bold text-primary">{title}</h1>
      <p className="text-muted-foreground">Disponible dans {milestone}.</p>
    </RequireRole>
  )
}
