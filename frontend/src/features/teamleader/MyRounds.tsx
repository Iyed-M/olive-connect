import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  getRound,
  listRounds,
  ROUND_STATUS_LABEL,
  roundDateLabel,
  STOP_STATUS_LABEL,
  type CollectionRound,
  type RoundStatus,
} from '@/features/collection/planning'

export function MyRoundsPage() {
  return (
    <RequireRole role="TEAM_LEADER">
      <MyRounds />
    </RequireRole>
  )
}

function MyRounds() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const roundsQuery = useQuery({ queryKey: ['rounds', 'mine'], queryFn: () => listRounds() })
  const detailQuery = useQuery({
    queryKey: ['round', selectedId],
    queryFn: () => getRound(selectedId!),
    enabled: Boolean(selectedId),
  })
  const rounds = roundsQuery.data?.content ?? []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-2">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">My rounds</h1>
          <p className="text-sm text-muted-foreground">Read-only route plan for your assigned collection rounds.</p>
        </div>
        <Button type="button" variant="outline" className="shrink-0" onClick={() => void roundsQuery.refetch()} disabled={roundsQuery.isFetching}>
          {roundsQuery.isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {roundsQuery.isPending && <p className="text-muted-foreground">Loading your rounds…</p>}
      {roundsQuery.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(roundsQuery.error, 'Could not load your rounds.')}</p>}
      {roundsQuery.data && rounds.length === 0 && <Card><CardContent className="py-6"><p className="text-muted-foreground">You have no assigned rounds yet.</p></CardContent></Card>}

      {rounds.length > 0 && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
          <ul className="flex min-w-0 flex-col gap-2" aria-label="My rounds list">
            {rounds.map((round) => (
              <li key={round.id}>
                <button
                  type="button"
                  aria-expanded={selectedId === round.id}
                  onClick={() => setSelectedId((current) => current === round.id ? null : round.id)}
                  className={`flex min-h-20 w-full flex-col gap-1 rounded-xl border p-3 text-left ${selectedId === round.id ? 'border-primary bg-primary/10 ring-2 ring-primary/20' : 'bg-card hover:bg-muted/50'}`}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="font-medium">{round.code}</span>
                    <RoundStatusBadge status={round.status} />
                  </span>
                  <span className="text-sm text-muted-foreground">{roundDateLabel(round.date)} · {round.startTime}–{round.endTime}</span>
                  <span className="text-xs text-muted-foreground">{round.stops.length} stop(s)</span>
                </button>
              </li>
            ))}
          </ul>

          <section aria-live="polite" aria-labelledby="round-detail-heading">
            {!selectedId && <Card><CardContent className="py-6"><p className="text-muted-foreground">Select a round to view its route and stops.</p></CardContent></Card>}
            {selectedId && detailQuery.isPending && <p className="text-muted-foreground">Loading round details…</p>}
            {selectedId && detailQuery.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(detailQuery.error, 'Could not load this round.')}</p>}
            {detailQuery.data && <RoundDetail round={detailQuery.data} />}
          </section>
        </div>
      )}
    </div>
  )
}

function RoundDetail({ round }: { round: CollectionRound }) {
  return (
    <Card>
      <CardHeader className="gap-2">
        <CardTitle id="round-detail-heading" className="flex flex-wrap items-center justify-between gap-2">
          <span>{round.code}</span>
          <RoundStatusBadge status={round.status} />
        </CardTitle>
        <p className="text-sm text-muted-foreground">{roundDateLabel(round.date)} · {round.startTime}–{round.endTime}</p>
        {round.mill?.name && <p className="text-sm text-muted-foreground">Destination: {round.mill.name}</p>}
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          <span>{round.stops.length} stop(s)</span>
          <span>Estimated {Math.round(round.estimatedTotalKg).toLocaleString()} kg</span>
          <span>{round.workerIds.length} worker(s)</span>
        </div>
        <ol className="flex flex-col gap-2">
          {round.stops.map((stop) => (
            <li key={`${stop.order}-${stop.harvestRequestId}`} className="rounded-lg border p-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-medium">{stop.order}. {stop.orchardName}</p>
                  <p className="text-sm text-muted-foreground">Planned arrival {stop.plannedArrival} · {Math.round(stop.estimatedKg).toLocaleString()} kg</p>
                </div>
                <Badge variant={stop.status === 'SKIPPED' ? 'destructive' : stop.status === 'DONE' ? 'default' : 'secondary'}>{STOP_STATUS_LABEL[stop.status]}</Badge>
              </div>
            </li>
          ))}
        </ol>
        <p className="text-xs text-muted-foreground">Field execution controls are not available in M5.</p>
      </CardContent>
    </Card>
  )
}

function RoundStatusBadge({ status }: { status: RoundStatus }) {
  return <Badge variant={status === 'CANCELLED' ? 'destructive' : status === 'COMPLETED' ? 'default' : 'secondary'}>{ROUND_STATUS_LABEL[status]}</Badge>
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.detail : fallback
}
