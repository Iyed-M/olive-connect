import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { OrchardForm } from '@/features/farmer/OrchardForm'
import { getOrchard } from '@/features/farmer/orchards'

export const Route = createFileRoute('/_app/farmer/orchards/$id')({ component: Page })

function Page() {
  const { id } = Route.useParams()
  const query = useQuery({ queryKey: ['orchard', id], queryFn: () => getOrchard(id) })
  const error = query.error instanceof ApiError ? query.error : null
  return <RequireRole role="FARMER">
    {query.isPending && <p className="text-muted-foreground">Loading orchard...</p>}
    {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Could not load the orchard.'}</p>}
    {query.data && <OrchardForm orchard={query.data} />}
  </RequireRole>
}
