import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { RequireRole } from '@/components/RequireRole'
import { Card, CardContent } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { ReceptionForm } from '@/features/mill/ReceptionForm'
import { findIncomingStop, formatReceptionError, getAssignedMillId, listIncomingRounds } from '@/features/mill/receptions'

export const Route = createFileRoute('/_app/mill/receptions/new')({
  validateSearch: (search: Record<string, unknown>) => ({
    roundId: typeof search.roundId === 'string' ? search.roundId : '',
    stopOrder: typeof search.stopOrder === 'string' ? search.stopOrder : '',
  }),
  component: Page,
})

function Page() {
  return (
    <RequireRole role="MILL_MANAGER">
      <ReceptionPage />
    </RequireRole>
  )
}

function ReceptionPage() {
  const { roundId, stopOrder } = Route.useSearch()
  const millId = getAssignedMillId()
  const parsedStopOrder = Number(stopOrder)
  const query = useQuery({
    queryKey: ['mill-incoming', millId],
    queryFn: () => listIncomingRounds(millId ?? ''),
    enabled: Boolean(millId),
    staleTime: 30_000,
  })
  const target = query.data && Number.isInteger(parsedStopOrder) ? findIncomingStop(query.data, roundId, parsedStopOrder) : null

  if (!millId) return <Message tone="error">Aucun moulin n’est associé à votre session. Demandez à un administrateur de vérifier votre affectation.</Message>
  if (query.isPending) return <p className="text-muted-foreground">Chargement de la tournée…</p>
  if (query.isError) return <Message tone="error">{formatReceptionError(query.error instanceof ApiError ? query.error : new Error('load failed'))}</Message>
  if (!target) return <Message tone="error">La tournée ou l’arrêt sélectionné n’est plus dans la file du jour.</Message>

  return <ReceptionForm millId={millId} round={target.round} stop={target.stop} />
}

function Message({ children, tone }: { children: string; tone: 'error' | 'info' }) {
  return (
    <Card>
      <CardContent className={tone === 'error' ? 'py-6 text-sm text-destructive' : 'py-6 text-sm'} role={tone === 'error' ? 'alert' : undefined}>
        {children}
      </CardContent>
    </Card>
  )
}
