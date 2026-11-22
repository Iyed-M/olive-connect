import { useState } from 'react'
import { Link, createFileRoute } from '@tanstack/react-router'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { ROLE_LABEL, ROLE_PRIORITY } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { PAGE_SIZE, listUsers, type UserFilters } from '@/admin/users'
import { RequireRole } from '@/components/RequireRole'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

export const Route = createFileRoute('/_app/admin/users/')({ component: Page })

const selectClass = 'h-11 w-full rounded-md border bg-background px-2 text-sm'

function Page() {
  return (
    <RequireRole role="ADMIN">
      <UsersList />
    </RequireRole>
  )
}

function UsersList() {
  const [filters, setFilters] = useState<UserFilters>({ role: '', enabled: '', page: 0 })
  const query = useQuery({
    queryKey: ['users', filters],
    queryFn: () => listUsers(filters),
    placeholderData: keepPreviousData,
  })
  const data = query.data
  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <h1 className="font-heading text-2xl font-bold text-primary">Utilisateurs</h1>
        <Link to="/admin/users/new" className={buttonVariants({ className: 'h-11' })}>
          <Plus aria-hidden /> Nouvel utilisateur
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <select
          aria-label="Filtrer par rôle"
          value={filters.role}
          onChange={(e) => setFilters({ ...filters, role: e.target.value as UserFilters['role'], page: 0 })}
          className={selectClass}
        >
          <option value="">Tous les rôles</option>
          {ROLE_PRIORITY.map((r) => (
            <option key={r} value={r}>
              {ROLE_LABEL[r]}
            </option>
          ))}
        </select>
        <select
          aria-label="Filtrer par statut"
          value={filters.enabled}
          onChange={(e) => setFilters({ ...filters, enabled: e.target.value as UserFilters['enabled'], page: 0 })}
          className={selectClass}
        >
          <option value="">Actifs et désactivés</option>
          <option value="true">Actifs</option>
          <option value="false">Désactivés</option>
        </select>
      </div>

      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Impossible de charger les utilisateurs.
        </p>
      )}
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {data && data.content.length === 0 && <p className="text-muted-foreground">Aucun utilisateur trouvé.</p>}

      {data && data.content.length > 0 && (
        <>
          {/* Cards below md */}
          <ul className="flex flex-col gap-2 md:hidden">
            {data.content.map((u) => (
              <li key={u.id}>
                <Link to="/admin/users/$id" params={{ id: u.id }}>
                  <Card>
                    <CardContent className="flex flex-col gap-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">
                          {u.firstName} {u.lastName}
                        </span>
                        <StatusBadge enabled={u.enabled} />
                      </div>
                      <span className="break-all text-sm text-muted-foreground">{u.email}</span>
                      <RoleBadges roles={u.roles} />
                    </CardContent>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>

          {/* Table from md */}
          <table className="hidden w-full text-left text-sm md:table">
            <thead className="border-b text-muted-foreground">
              <tr>
                <th className="py-2">Nom</th>
                <th>Email</th>
                <th>Rôles</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => (
                <tr key={u.id} className="border-b">
                  <td className="py-2">
                    <Link to="/admin/users/$id" params={{ id: u.id }} className="font-medium text-primary hover:underline">
                      {u.firstName} {u.lastName}
                    </Link>
                  </td>
                  <td>{u.email}</td>
                  <td>
                    <RoleBadges roles={u.roles} />
                  </td>
                  <td>
                    <StatusBadge enabled={u.enabled} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex items-center justify-between gap-2">
            <Button
              variant="outline"
              className="h-11"
              disabled={filters.page === 0}
              onClick={() => setFilters({ ...filters, page: filters.page - 1 })}
            >
              Précédent
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {filters.page + 1} sur {lastPage + 1}
            </span>
            <Button
              variant="outline"
              className="h-11"
              disabled={filters.page >= lastPage}
              onClick={() => setFilters({ ...filters, page: filters.page + 1 })}
            >
              Suivant
            </Button>
          </div>
        </>
      )}
    </>
  )
}

function RoleBadges({ roles }: { roles: Role[] }) {
  return (
    <span className="flex flex-wrap gap-1">
      {roles.map((r) => (
        <Badge key={r} variant="secondary">
          {ROLE_LABEL[r]}
        </Badge>
      ))}
    </span>
  )
}

function StatusBadge({ enabled }: { enabled: boolean }) {
  return <Badge variant={enabled ? 'default' : 'outline'}>{enabled ? 'Actif' : 'Désactivé'}</Badge>
}
