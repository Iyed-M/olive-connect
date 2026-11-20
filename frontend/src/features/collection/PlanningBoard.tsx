import { useCallback, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { listHarvestRequests } from '@/features/farmer/harvestRequests'
import { PlanningMap } from './PlanningMap'
import { RoundForm } from './RoundForm'
import {
  cancelRound,
  formatKg,
  formatRoundWarning,
  getPlanningSuggestions,
  listMills,
  listRounds,
  roundDateLabel,
  sortValidatedRequests,
  type CollectionRound,
  type PlanningSuggestions,
  type RoundMutationResult,
} from './planning'

export function PlanningBoardPage() {
  return (
    <RequireRole role="COLLECTION_MANAGER">
      <PlanningBoard />
    </RequireRole>
  )
}

function PlanningBoard() {
  const queryClient = useQueryClient()
  const [planningDate, setPlanningDate] = useState(localDate(new Date()))
  const [selectedRequestIds, setSelectedRequestIds] = useState<string[]>([])
  const [editingRound, setEditingRound] = useState<CollectionRound | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const requestsQuery = useQuery({
    queryKey: ['planning-requests'],
    queryFn: () => listHarvestRequests({ status: 'VALIDATED', page: 0, size: 100 }),
  })
  const suggestionsQuery = useQuery({
    queryKey: ['planning-suggestions', planningDate],
    queryFn: () => getPlanningSuggestions(planningDate),
    enabled: Boolean(planningDate),
  })
  const millsQuery = useQuery({ queryKey: ['mills'], queryFn: listMills, staleTime: 60_000 })
  const roundsQuery = useQuery({
    queryKey: ['rounds', 'planning', planningDate],
    queryFn: () => listRounds({ from: planningDate, to: planningDate, status: 'PLANNED' }),
    enabled: Boolean(planningDate),
  })
  const requests = useMemo(() => sortValidatedRequests(requestsQuery.data?.content ?? []), [requestsQuery.data?.content])
  const knownRequestIds = useMemo(() => new Set(requests.map((request) => request.id)), [requests])
  const staleRequestIds = selectedRequestIds.filter((id) => !knownRequestIds.has(id) && !editingRound?.stops.some((stop) => stop.harvestRequestId === id))

  const cancelMutation = useMutation({
    mutationFn: cancelRound,
    onSuccess: async () => {
      setNotice('Tournée annulée et ressources libérées.')
      await queryClient.invalidateQueries({ queryKey: ['rounds'] })
      await queryClient.invalidateQueries({ queryKey: ['planning-requests'] })
    },
  })

  const onDateChange = useCallback((date: string) => {
    if (date && date !== planningDate) setPlanningDate(date)
  }, [planningDate])

  function toggleRequest(id: string) {
    setNotice(null)
    setEditingRound(null)
    setSelectedRequestIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id])
  }

  function chooseSuggestion(suggestion: PlanningSuggestions) {
    const group = suggestion.groups[0]
    if (!group) return
    setEditingRound(null)
    setSelectedRequestIds(group.requestIds)
    setNotice(`Suggestion sélectionnée : ${group.requestIds.length} demande(s), ${formatKg(group.estimatedTotalKg)} estimés.`)
  }

  function startEditing(round: CollectionRound) {
    setNotice(null)
    setEditingRound(round)
    setSelectedRequestIds(round.stops.map((stop) => stop.harvestRequestId))
    setPlanningDate(round.date)
  }

  function startNewRound() {
    setNotice(null)
    setEditingRound(null)
    setSelectedRequestIds([])
  }

  function saved(result: RoundMutationResult) {
    setNotice(`${result.round.code} ${editingRound ? 'mise à jour' : 'créée'} avec succès.`)
    if (result.warnings.length > 0) setNotice(`${result.round.code} enregistrée. ${result.warnings.map(formatRoundWarning).join(' ')}`)
    setEditingRound(null)
    setSelectedRequestIds([])
  }

  function cancelExisting(round: CollectionRound) {
    if (!window.confirm(`Annuler ${round.code} ? Ses réservations seront libérées.`)) return
    cancelMutation.mutate(round.id)
  }

  const loadError = (error: unknown, fallback: string) => error instanceof ApiError ? error.detail : fallback

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Tableau de planification</h1>
          <p className="text-sm text-muted-foreground">Les demandes validées sont classées par priorité. Sélectionnez les étapes, puis réservez une équipe complète.</p>
        </div>
        <div className="flex flex-col gap-2 sm:w-48">
          <Label htmlFor="planning-date">Date de planification</Label>
          <Input id="planning-date" type="date" value={planningDate} onChange={(event) => setPlanningDate(event.target.value)} />
        </div>
      </div>

      {notice && <p role="status" className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm text-primary">{notice}</p>}
      {cancelMutation.error && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{loadError(cancelMutation.error, 'La tournée n’a pas pu être annulée.')}</p>}

      <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-[minmax(14rem,0.8fr)_minmax(18rem,1.2fr)]">
        <section className="flex min-w-0 flex-col gap-3" aria-labelledby="validated-requests-heading">
          <div className="flex items-center justify-between gap-2">
            <h2 id="validated-requests-heading" className="font-heading text-xl font-bold text-primary">Demandes validées</h2>
            <span className="text-sm text-muted-foreground">{selectedRequestIds.length} sélectionnée(s)</span>
          </div>
          {requestsQuery.isPending && <p className="text-sm text-muted-foreground">Chargement des demandes…</p>}
          {requestsQuery.isError && <p role="alert" className="text-sm text-destructive">{loadError(requestsQuery.error, 'Impossible de charger les demandes validées.')}</p>}
          {requestsQuery.data && requests.length === 0 && <Card><CardContent className="py-5"><p className="text-sm text-muted-foreground">Aucune demande validée n’attend d’être planifiée.</p></CardContent></Card>}
          {staleRequestIds.length > 0 && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Certaines demandes sélectionnées ne sont plus validées. Vérifiez la sélection avant l’enregistrement.</p>}
          <div className="flex flex-col gap-2">
            {requests.map((request) => {
              const selected = selectedRequestIds.includes(request.id)
              return (
                <button
                  key={request.id}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => toggleRequest(request.id)}
                  className={`min-h-20 rounded-xl border p-3 text-left transition-colors ${selected ? 'border-primary bg-primary/10 ring-2 ring-primary/20' : 'bg-card hover:bg-muted/50'}`}
                >
                  <span className="flex items-start justify-between gap-2">
                    <span className="font-medium">{request.orchard.name}</span>
                    <span className="shrink-0 rounded-full bg-secondary px-2 py-1 text-xs">{request.type === 'URGENT' ? 'Urgente' : 'Planifiée'}</span>
                  </span>
                  <span className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    <span>Priorité {request.priorityScore.toFixed(1)}</span>
                    <span>{formatKg(request.estimatedQuantityKg)}</span>
                    <span>{request.orchard.delegation.replaceAll('_', ' ')}</span>
                  </span>
                </button>
              )
            })}
          </div>
        </section>

        <section className="flex min-w-0 flex-col gap-3" aria-labelledby="request-map-heading">
          <div className="flex items-center justify-between gap-2">
            <h2 id="request-map-heading" className="font-heading text-xl font-bold text-primary">Carte des demandes</h2>
            {suggestionsQuery.isFetching && <span className="text-xs text-muted-foreground">Actualisation des suggestions…</span>}
          </div>
          <PlanningMap requests={requests} selectedRequestIds={selectedRequestIds} onToggle={toggleRequest} />
          {suggestionsQuery.isError && <p role="alert" className="text-sm text-destructive">{loadError(suggestionsQuery.error, 'Impossible de charger les suggestions de planification.')}</p>}
          {suggestionsQuery.data?.groups.length ? <SuggestionCard suggestions={suggestionsQuery.data} onChoose={() => chooseSuggestion(suggestionsQuery.data!)} /> : <p className="text-sm text-muted-foreground">Aucune suggestion de regroupement n’est disponible pour cette date.</p>}
        </section>
      </div>

      <section className="flex flex-col gap-3" aria-labelledby="round-form-heading">
        <div className="flex items-center justify-between gap-2">
          <h2 id="round-form-heading" className="font-heading text-xl font-bold text-primary">{editingRound ? 'Modifier la tournée planifiée' : 'Détails de la tournée'}</h2>
          {editingRound && <Button type="button" variant="outline" onClick={startNewRound}>Planifier une nouvelle tournée</Button>}
        </div>
        <RoundForm
          key={editingRound?.id ?? 'new-round'}
          requests={requests}
          selectedRequestIds={selectedRequestIds}
          suggestions={suggestionsQuery.data}
          mills={millsQuery.data ?? []}
          existingRound={editingRound}
          defaultDate={planningDate}
          onDateChange={onDateChange}
          onSaved={saved}
          onCancelEdit={startNewRound}
        />
        {millsQuery.isPending && <p className="text-sm text-muted-foreground">Chargement des moulins…</p>}
        {millsQuery.isError && <p role="alert" className="text-sm text-destructive">{loadError(millsQuery.error, 'Impossible de charger les moulins.')}</p>}
      </section>

      <section className="flex flex-col gap-3" aria-labelledby="planned-rounds-heading">
        <div className="flex items-center justify-between gap-2">
          <h2 id="planned-rounds-heading" className="font-heading text-xl font-bold text-primary">Tournées planifiées pour le {roundDateLabel(planningDate)}</h2>
          {roundsQuery.isFetching && <span className="text-xs text-muted-foreground">Actualisation…</span>}
        </div>
        {roundsQuery.isError && <p role="alert" className="text-sm text-destructive">{loadError(roundsQuery.error, 'Impossible de charger les tournées planifiées.')}</p>}
        {roundsQuery.data && roundsQuery.data.content.length === 0 && <p className="text-sm text-muted-foreground">Aucune tournée planifiée pour cette date.</p>}
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {roundsQuery.data?.content.map((round) => (
            <PlannedRoundCard
              key={round.id}
              round={round}
              cancelling={cancelMutation.isPending && cancelMutation.variables === round.id}
              onEdit={() => startEditing(round)}
              onCancel={() => cancelExisting(round)}
            />
          ))}
        </div>
      </section>
    </div>
  )
}

function SuggestionCard({ suggestions, onChoose }: { suggestions: PlanningSuggestions; onChoose: () => void }) {
  const first = suggestions.groups[0]
  if (!first) return null
  return (
    <Card>
      <CardHeader className="gap-2">
        <CardTitle>Regroupement suggéré</CardTitle>
        <p className="text-sm text-muted-foreground">Le premier groupe suit la priorité et la proximité des vergers pour la date sélectionnée.</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-2 text-sm">
          <span>{first.requestIds.length} demande(s)</span>
          <span>{formatKg(first.estimatedTotalKg)}</span>
        </div>
        {suggestions.warnings.map((warning) => <p key={`${warning.code}-${warning.message}`} className="text-sm text-amber-900">{formatRoundWarning(warning)}</p>)}
        <Button type="button" variant="outline" className="w-fit" onClick={onChoose}>Utiliser cette suggestion</Button>
      </CardContent>
    </Card>
  )
}

function PlannedRoundCard({
  round,
  cancelling,
  onEdit,
  onCancel,
}: {
  round: CollectionRound
  cancelling: boolean
  onEdit: () => void
  onCancel: () => void
}) {
  return (
    <Card>
      <CardHeader className="gap-1">
        <CardTitle className="flex items-center justify-between gap-2">
          <span>{round.code}</span>
          <span className="text-sm font-normal text-muted-foreground">{round.startTime}–{round.endTime}</span>
        </CardTitle>
        <p className="text-sm text-muted-foreground">{round.stops.length} étape(s) · {formatKg(round.estimatedTotalKg)}</p>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={onEdit} disabled={cancelling}>Modifier</Button>
        <Button type="button" variant="destructive" onClick={onCancel} disabled={cancelling}>{cancelling ? 'Annulation…' : 'Annuler la tournée'}</Button>
      </CardContent>
    </Card>
  )
}

function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
