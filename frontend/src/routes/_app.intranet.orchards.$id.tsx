import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { CorrectionHistory } from '@/components/CorrectionHistory'
import { RequireRole } from '@/components/RequireRole'
import { OrchardForm } from '@/features/farmer/OrchardForm'
import { getOrchard } from '@/features/farmer/orchards'

export const Route = createFileRoute('/_app/intranet/orchards/$id')({ component: Page })

function Page() {
  const { id } = Route.useParams()
  const query = useQuery({ queryKey: ['orchard', id], queryFn: () => getOrchard(id) })
  const error = query.error instanceof ApiError ? query.error : null
  return (
    <RequireRole role={['COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN']}>
      {query.isPending && <p className="text-muted-foreground">Chargement du verger…</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Impossible de charger le verger.'}</p>}
      {query.data && <OrchardForm key={query.data.version} orchard={query.data} staff />}
      {query.data && <CorrectionHistory items={query.data.corrections} />}
    </RequireRole>
  )
}
