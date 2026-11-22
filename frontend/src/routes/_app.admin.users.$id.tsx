import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { getUser } from '@/admin/users'
import { UserForm } from '@/admin/UserForm'
import { RequireRole } from '@/components/RequireRole'

export const Route = createFileRoute('/_app/admin/users/$id')({ component: Page })

function Page() {
  return (
    <RequireRole role="ADMIN">
      <UserDetail />
    </RequireRole>
  )
}

function UserDetail() {
  const { id } = Route.useParams()
  const query = useQuery({ queryKey: ['user', id], queryFn: () => getUser(id) })

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Modifier l’utilisateur</h1>
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          {query.error.message}
        </p>
      )}
      {/* key: a new version (after a save or enable/disable) rebuilds the form with fresh values */}
      {query.data && <UserForm key={query.data.version} user={query.data} />}
    </>
  )
}
