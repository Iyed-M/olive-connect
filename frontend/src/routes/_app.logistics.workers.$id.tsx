import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { RequireRole } from '@/components/RequireRole'
import { UnavailabilityPanel } from '@/logistics/UnavailabilityPanel'
import { WorkerForm } from '@/logistics/WorkerForm'
import { addWorkerUnavailability, getWorker } from '@/logistics/resources'

export const Route = createFileRoute('/_app/logistics/workers/$id')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'ADMIN']}>
      <WorkerDetail />
    </RequireRole>
  )
}

function WorkerDetail() {
  const { id } = Route.useParams()
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['worker', id], queryFn: () => getWorker(id) })

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Modifier l’ouvrier</h1>
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          {query.error.message}
        </p>
      )}
      {/* key: a new version rebuilds the form with fresh values */}
      {query.data && <WorkerForm key={query.data.version} worker={query.data} />}
      {query.data && (
        <UnavailabilityPanel
          items={query.data.unavailabilities}
          add={(body) => addWorkerUnavailability(id, body)}
          onAdded={() => queryClient.invalidateQueries({ queryKey: ['worker', id] })}
        />
      )}
    </>
  )
}
