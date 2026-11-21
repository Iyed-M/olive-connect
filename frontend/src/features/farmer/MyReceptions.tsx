import { useQuery } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { RequireRole } from '@/components/RequireRole'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { formatReceptionError, formatReceptionKg, formatReceptionRate, listReceptions, qualityGradeLabel, type MillReception } from '@/features/mill/receptions'

export function MyReceptionsPage() {
  return (
    <RequireRole role="FARMER">
      <MyReceptions />
    </RequireRole>
  )
}

function MyReceptions() {
  const query = useQuery({
    queryKey: ['receptions', { mine: true }],
    queryFn: () => listReceptions(true),
  })
  const error = query.error instanceof ApiError ? formatReceptionError(query.error) : query.error ? formatReceptionError(query.error) : null

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Mes réceptions</h1>
          <p className="text-sm text-muted-foreground">Retrouvez les pesées de vos récoltes au moulin.</p>
        </div>
        <Button type="button" variant="outline" size="icon" aria-label="Actualiser" onClick={() => void query.refetch()} disabled={query.isFetching}>
          <RefreshCw aria-hidden />
        </Button>
      </div>

      {query.isPending && <p className="text-muted-foreground">Chargement de vos réceptions…</p>}
      {query.isError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error ?? 'Impossible de charger vos réceptions.'}</p>}
      {query.data && query.data.content.length === 0 && (
        <Card>
          <CardContent className="py-6"><p className="text-sm text-muted-foreground">Aucune réception n’est encore enregistrée pour vos vergers.</p></CardContent>
        </Card>
      )}
      {query.data && query.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {query.data.content.map((reception) => <ReceptionCard key={reception.id} reception={reception} />)}
        </div>
      )}
    </div>
  )
}

function ReceptionCard({ reception }: { reception: MillReception }) {
  return (
    <Card>
      <CardHeader className="gap-1">
        <CardTitle className="flex flex-wrap items-start justify-between gap-2">
          <span>{reception.orchardName ?? `Verger ${reception.orchardId}`}</span>
          <span className="text-sm font-normal text-muted-foreground">{formatDate(reception.receivedAt)}</span>
        </CardTitle>
        <p className="text-sm text-muted-foreground">{reception.millName ?? `Moulin ${reception.millId}`} · Tournée {reception.roundId}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
          <Metric label="Net" value={formatReceptionKg(reception.netWeightKg)} />
          <Metric label="Brut" value={formatReceptionKg(reception.grossWeightKg)} />
          <Metric label="Tare" value={formatReceptionKg(reception.tareKg)} />
          <Metric label="Remplissage" value={formatReceptionRate(reception.fillRate)} />
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          <span><strong>Qualité :</strong> {qualityGradeLabel(reception.quality.grade)}</span>
          {reception.binCode && <span><strong>Benne :</strong> {reception.binCode}</span>}
          {reception.varietyCode && <span><strong>Variété :</strong> {reception.varietyCode}</span>}
          {reception.delegation && <span><strong>Délégation :</strong> {displayCode(reception.delegation)}</span>}
        </div>
        {reception.leadTimeHours != null && <p className="text-xs text-muted-foreground">Délai entre validation et réception : {reception.leadTimeHours.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} h</p>}
      </CardContent>
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border bg-background p-2"><p className="text-xs text-muted-foreground">{label}</p><p className="font-mono font-semibold">{value}</p></div>
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeZone: 'Africa/Tunis' }).format(date)
}

function displayCode(value: string): string {
  return value.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, (letter) => letter.toUpperCase())
}
