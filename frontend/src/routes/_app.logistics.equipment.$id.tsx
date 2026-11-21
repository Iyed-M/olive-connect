import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CorrectionHistory } from '@/components/CorrectionHistory'
import { RequireRole } from '@/components/RequireRole'
import { EquipmentForm } from '@/logistics/EquipmentForm'
import { UnavailabilityPanel } from '@/logistics/UnavailabilityPanel'
import { addEquipmentUnavailability, getEquipment } from '@/logistics/resources'

export const Route = createFileRoute('/_app/logistics/equipment/$id')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'ADMIN']}>
      <EquipmentDetail />
    </RequireRole>
  )
}

function EquipmentDetail() {
  const { id } = Route.useParams()
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['equipment-item', id], queryFn: () => getEquipment(id) })

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Edit equipment</h1>
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          {query.error.message}
        </p>
      )}
      {/* key: a new version rebuilds the form with fresh values */}
      {query.data && <EquipmentForm key={query.data.version} equipment={query.data} />}
      {query.data && <CorrectionHistory items={query.data.corrections} />}
      {query.data && (
        <UnavailabilityPanel
          items={query.data.unavailabilities}
          add={(body) => addEquipmentUnavailability(id, body)}
          onAdded={() => queryClient.invalidateQueries({ queryKey: ['equipment-item', id] })}
        />
      )}
    </>
  )
}
