import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { RefreshCw, Scale } from 'lucide-react'
import { RequireRole } from '@/components/RequireRole'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import {
  formatReceptionError,
  formatReceptionKg,
  getAssignedMillId,
  getBinsForStop,
  listIncomingRounds,
  type IncomingRound,
  type IncomingStop,
} from './receptions'

export function IncomingRoundsPage() {
  return (
    <RequireRole role="MILL_MANAGER">
      <IncomingRounds />
    </RequireRole>
  )
}

function IncomingRounds() {
  const millId = getAssignedMillId()
  const query = useQuery({
    queryKey: ['mill-incoming', millId],
    queryFn: () => listIncomingRounds(millId ?? ''),
    enabled: Boolean(millId),
    staleTime: 30_000,
  })

  const error = query.error instanceof ApiError ? formatReceptionError(query.error) : query.error ? formatReceptionError(query.error) : null

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Réceptions du jour</h1>
          <p className="text-sm text-muted-foreground">Les tournées qui livrent votre moulin sont prêtes à être pesées.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/mill/capacity" className={buttonVariants({ variant: 'outline', className: 'h-11' })}>Publier la capacité</Link>
          <Button type="button" variant="outline" className="h-11" onClick={() => void query.refetch()} disabled={!millId || query.isFetching}>
            <RefreshCw aria-hidden /> {query.isFetching ? 'Actualisation…' : 'Actualiser'}
          </Button>
        </div>
      </div>

      {!millId && (
        <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
          Aucun moulin n’est associé à votre session. Demandez à un administrateur de vérifier votre affectation.
        </p>
      )}
      {query.isPending && millId && <p className="text-muted-foreground">Chargement des tournées…</p>}
      {query.isError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error ?? 'Impossible de charger les tournées entrantes.'}</p>}
      {query.data && query.data.length === 0 && (
        <Card>
          <CardContent className="py-6">
            <p className="text-sm text-muted-foreground">Aucune tournée n’est attendue aujourd’hui.</p>
          </CardContent>
        </Card>
      )}
      {query.data && query.data.length > 0 && (
        <div className="flex flex-col gap-3">
          {query.data.map((round) => <IncomingRoundCard key={round.id} round={round} />)}
        </div>
      )}
    </div>
  )
}

function IncomingRoundCard({ round }: { round: IncomingRound }) {
  return (
    <Card>
      <CardHeader className="gap-1">
        <CardTitle className="flex flex-wrap items-center justify-between gap-2">
          <span>{round.code}</span>
          <span className="text-sm font-normal text-muted-foreground">{round.startTime}–{round.endTime}</span>
        </CardTitle>
        <p className="text-sm text-muted-foreground">
          {round.stops.length} arrêt(s) · {formatReceptionKg(round.totalHarvestedKg || round.estimatedTotalKg)} à réceptionner
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {round.stops.map((stop) => <IncomingStopRow key={stop.order} round={round} stop={stop} />)}
      </CardContent>
    </Card>
  )
}

function IncomingStopRow({ round, stop }: { round: IncomingRound; stop: IncomingStop }) {
  const bins = getBinsForStop(round, stop)
  const skipped = stop.status === 'SKIPPED'
  return (
    <div className="flex min-w-0 flex-col gap-3 rounded-lg border bg-background p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="font-medium">{stop.order}. {stop.orchardName}</p>
        <p className="text-sm text-muted-foreground">
          Arrivée {stop.plannedArrival || '—'} · {formatReceptionKg(stop.harvestedKg ?? stop.estimatedKg)} · {bins.length} benne(s) prévue(s)
        </p>
      </div>
      {skipped ? (
        <span className="text-sm text-muted-foreground">Arrêt ignoré</span>
      ) : (
        <Link
          to="/mill/receptions/new"
          search={{ roundId: round.id, stopOrder: String(stop.order) }}
          className={buttonVariants({ variant: 'outline', className: 'h-11 w-full sm:w-fit' })}
        >
          <Scale aria-hidden /> Enregistrer la pesée
        </Link>
      )}
    </div>
  )
}
