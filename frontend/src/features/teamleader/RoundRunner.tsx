import { useCallback, useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, MapPin, Play, SkipForward } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { Field } from '@/components/Field'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  getRound,
  ROUND_STATUS_LABEL,
  roundDateLabel,
  STOP_STATUS_LABEL,
  type CollectionRound,
  type RoundStop,
} from '@/features/collection/planning'
import {
  buildStopUpdate,
  createRoundDraft,
  createRoundMutation,
  createRoundRunnerStore,
  draftForServerRound,
  isRetryableMutationError,
  mergeRoundWithDraft,
  roundFromDraft,
  retryQueuedMutations,
  sendRoundMutation,
  type RoundDraft,
  type RoundMutation,
  type RoundRunnerStore,
  type StopDraft,
} from './roundRunner'

export function RoundRunnerPage({ id }: { id: string }) {
  return (
    <RequireRole role="TEAM_LEADER">
      <RoundRunner id={id} />
    </RequireRole>
  )
}

function RoundRunner({ id }: { id: string }) {
  const query = useQuery({ queryKey: ['round', id], queryFn: () => getRound(id), retry: false })
  const [store] = useState<RoundRunnerStore>(() => createRoundRunnerStore())
  const [round, setRound] = useState<CollectionRound | null>(null)
  const [draft, setDraft] = useState<RoundDraft | null>(null)
  const [draftLoading, setDraftLoading] = useState(true)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [pendingSync, setPendingSync] = useState(0)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [online, setOnline] = useState(() => typeof navigator === 'undefined' || navigator.onLine)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])

  useEffect(() => {
    let active = true
    void Promise.all([store.getDraft(id), store.listMutations(id)])
      .then(([savedDraft, queued]) => {
        if (!active) return
        const nextDraft = savedDraft ?? (query.data ? createRoundDraft(query.data) : null)
        const savedRound = savedDraft ? roundFromDraft(savedDraft) : null
        const nextRound = query.data
          ? (savedDraft ? mergeRoundWithDraft(query.data, savedDraft) : query.data)
          : savedRound
        setDraft(nextDraft)
        setRound(nextRound)
        setPendingSync(queued.length)
        setDraftLoading(false)
        if (!savedDraft && nextDraft) void store.saveDraft(nextDraft).catch(() => undefined)
        if (!query.data && query.isError && savedRound) {
          setInfo('Serveur indisponible : le brouillon enregistré est affiché hors connexion.')
        }
      })
      .catch(() => {
        if (!active) return
        const nextDraft = query.data ? createRoundDraft(query.data) : null
        setDraft(nextDraft)
        setRound(query.data ?? null)
        setDraftLoading(false)
        setInfo('Le brouillon hors connexion n’est pas disponible sur cet appareil.')
      })
    return () => {
      active = false
    }
  }, [id, query.data, query.isError, store])

  const refetchRound = query.refetch
  const syncQueue = useCallback(async () => {
    if (typeof navigator !== 'undefined' && !navigator.onLine) return
    try {
      const result = await retryQueuedMutations(store, sendRoundMutation, id)
      setPendingSync(result.pending)
      if (result.synced > 0) {
        if (result.responses.some(({ mutation }) => mutation.action === 'COMPLETE')) {
          await store.deleteDraft(id)
        }
        await refetchRound()
        setInfo(`${result.synced} action(s) hors connexion synchronisée(s).`)
      }
      if (result.error && !isRetryableMutationError(result.error)) {
        setError(roundRunnerError(result.error))
      }
    } catch (caught) {
      setInfo(isRetryableMutationError(caught) ? 'Synchronisation en attente de la connexion.' : roundRunnerError(caught))
    }
  }, [id, refetchRound, store])

  useEffect(() => {
    const initialSync = window.setTimeout(() => void syncQueue(), 0)
    const onOnline = () => void syncQueue()
    window.addEventListener('online', onOnline)
    return () => {
      window.clearTimeout(initialSync)
      window.removeEventListener('online', onOnline)
    }
  }, [syncQueue])

  const updateDraft = useCallback((order: number, changes: Partial<StopDraft>) => {
    setDraft((current) => {
      if (!current) return current
      const key = String(order)
      const next = {
        ...current,
        updatedAt: Date.now(),
        stops: { ...current.stops, [key]: { ...current.stops[key], ...changes } },
      }
      void store.saveDraft(next).catch(() => undefined)
      return next
    })
  }, [store])

  const runMutation = useCallback(async (mutation: RoundMutation) => {
    if (!round || !draft) return
    setBusyAction(mutation.action === 'STOP' ? `stop-${mutation.order}` : mutation.action)
    setError(null)
    setInfo(null)

    const previousRound = round
    const optimisticRound = applyOptimisticMutation(round, mutation)
    const optimisticDraft = draftForServerRound(draft, optimisticRound, mutation)
    setRound(optimisticRound)
    setDraft(optimisticDraft)
    await store.saveDraft(optimisticDraft).catch(() => undefined)

    try {
      const response = await sendRoundMutation(mutation)
      if (mutation.action === 'COMPLETE') {
        await store.deleteDraft(id).catch(() => undefined)
        setRound(response)
        setDraft(draftForServerRound(optimisticDraft, response, mutation))
      } else {
        const responseDraft = draftForServerRound(optimisticDraft, response, mutation)
        setRound(response)
        setDraft(responseDraft)
        await store.saveDraft(responseDraft).catch(() => undefined)
      }
      setPendingSync((await store.listMutations(id)).length)
      setInfo(mutationInfo(mutation))
    } catch (caught) {
      if (isRetryableMutationError(caught)) {
        await store.enqueueMutation(mutation)
        setPendingSync((await store.listMutations(id)).length)
        setInfo('Action enregistrée hors connexion : elle sera synchronisée dès que le réseau revient.')
      } else {
        setRound(previousRound)
        setDraft(draft)
        setError(roundRunnerError(caught))
      }
    } finally {
      setBusyAction(null)
    }
  }, [draft, id, round, store])

  if (draftLoading || (!round && query.isPending)) return <p className="text-muted-foreground">Chargement de la tournée…</p>
  if (!round && query.isError) return <p role="alert" className="text-sm text-destructive">{roundRunnerError(query.error)}</p>
  if (!round || !draft) return <p role="alert" className="text-sm text-destructive">Impossible d’ouvrir cette tournée.</p>

  const currentStop = round.stops[currentIndex]
  const currentDraft = currentStop ? draft.stops[String(currentStop.order)] ?? emptyStopDraft() : null
  const isBusy = busyAction !== null

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex min-w-0 items-start justify-between gap-3">
        <div className="min-w-0">
          <Link to="/rounds/mine" className="mb-2 inline-flex min-h-11 items-center gap-1 text-sm text-primary hover:underline">
            <ArrowLeft className="size-4" aria-hidden /> Mes tournées
          </Link>
          <h1 className="break-words font-heading text-2xl font-bold text-primary">{round.code}</h1>
          <p className="text-sm text-muted-foreground">{roundDateLabel(round.date)} · {round.startTime}–{round.endTime}</p>
        </div>
        <RoundStatusBadge status={round.status} />
      </div>

      {(!online || pendingSync > 0) && (
        <p role="status" className="rounded-lg bg-secondary px-3 py-2 text-sm text-secondary-foreground">
          {!online ? 'Hors connexion. ' : ''}
          {pendingSync > 0 ? `${pendingSync} action(s) en attente de synchronisation.` : 'Les brouillons sont conservés sur cet appareil.'}
        </p>
      )}
      {info && <p role="status" className="rounded-lg bg-muted px-3 py-2 text-sm">{info}</p>}
      {error && <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}

      {round.status === 'PLANNED' && (
        <Card>
          <CardContent className="flex flex-col gap-3 py-4">
            <p>La tournée est planifiée pour aujourd’hui. Démarrez-la quand l’équipe est prête.</p>
            <Button className="w-full sm:w-fit" disabled={isBusy} onClick={() => void runMutation(createRoundMutation({ roundId: id, action: 'START' }))}>
              <Play aria-hidden /> {busyAction === 'START' ? 'Démarrage…' : 'Démarrer la tournée'}
            </Button>
          </CardContent>
        </Card>
      )}

      {round.status === 'IN_PROGRESS' && round.stops.length > 0 && (
        <>
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-medium">Étape {currentIndex + 1} sur {round.stops.length}</p>
            <div className="flex gap-2">
              <Button type="button" size="icon" variant="outline" aria-label="Étape précédente" disabled={currentIndex === 0} onClick={() => setCurrentIndex((index) => Math.max(0, index - 1))}>
                <ArrowLeft aria-hidden />
              </Button>
              <Button type="button" size="icon" variant="outline" aria-label="Étape suivante" disabled={currentIndex === round.stops.length - 1} onClick={() => setCurrentIndex((index) => Math.min(round.stops.length - 1, index + 1))}>
                <ArrowRight aria-hidden />
              </Button>
            </div>
          </div>
          <nav aria-label="Étapes de la tournée" className="flex gap-2 overflow-x-auto pb-1">
            {round.stops.map((stop, index) => (
              <button
                key={stop.order}
                type="button"
                aria-current={index === currentIndex ? 'step' : undefined}
                onClick={() => setCurrentIndex(index)}
                className={`flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full border px-3 text-sm font-medium ${index === currentIndex ? 'border-primary bg-primary text-primary-foreground' : 'bg-card hover:bg-muted'}`}
              >
                {stop.order}
              </button>
            ))}
          </nav>
          {currentStop && currentDraft && (
            <StopCard
              stop={currentStop}
              draft={currentDraft}
              busy={isBusy}
              onChange={(changes) => updateDraft(currentStop.order, changes)}
              onStart={() => void runMutation(createRoundMutation({ roundId: id, action: 'STOP', order: currentStop.order, body: { status: 'IN_PROGRESS' } }))}
              onDone={() => submitStop(id, currentStop, currentDraft, 'DONE', runMutation, setError)}
              onSkip={() => submitStop(id, currentStop, currentDraft, 'SKIPPED', runMutation, setError)}
            />
          )}
          <Button type="button" variant="outline" className="w-full sm:w-fit" disabled={isBusy} onClick={() => void runMutation(createRoundMutation({ roundId: id, action: 'COMPLETE' }))}>
            <Check aria-hidden /> {busyAction === 'COMPLETE' ? 'Clôture…' : 'Clôturer la tournée'}
          </Button>
        </>
      )}

      {round.status === 'COMPLETED' && (
        <Card>
          <CardContent className="flex flex-col gap-3 py-6">
            <p className="font-medium">Tournée terminée</p>
            <p className="text-sm text-muted-foreground">Les arrêts terminés et ignorés ont été transmis.</p>
            <Link to="/rounds/mine" className={buttonVariants({ variant: 'outline', className: 'w-full sm:w-fit' })}>Retour aux tournées</Link>
          </CardContent>
        </Card>
      )}

      {round.status === 'CANCELLED' && <p className="text-muted-foreground">Cette tournée a été annulée par le gestionnaire.</p>}
    </div>
  )
}

function StopCard({
  stop,
  draft,
  busy,
  onChange,
  onStart,
  onDone,
  onSkip,
}: {
  stop: RoundStop
  draft: StopDraft
  busy: boolean
  onChange: (changes: Partial<StopDraft>) => void
  onStart: () => void
  onDone: () => void
  onSkip: () => void
}) {
  const navigation = stop.location
    ? `https://www.google.com/maps/dir/?api=1&destination=${stop.location.coordinates[1]},${stop.location.coordinates[0]}`
    : null
  const terminal = stop.status === 'DONE' || stop.status === 'SKIPPED'

  return (
    <Card>
      <CardHeader className="gap-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <CardTitle className="break-words">{stop.order}. {stop.orchardName}</CardTitle>
            <p className="text-sm text-muted-foreground">Arrivée prévue à {stop.plannedArrival} · estimation {Math.round(stop.estimatedKg).toLocaleString()} kg</p>
          </div>
          <StopStatusBadge status={stop.status} />
        </div>
        {navigation && (
          <a href={navigation} target="_blank" rel="noreferrer" className={buttonVariants({ variant: 'outline', className: 'w-full sm:w-fit' })}>
            <MapPin aria-hidden /> Ouvrir la navigation
          </a>
        )}
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {stop.status === 'PENDING' && (
          <Button type="button" className="w-full" disabled={busy} onClick={onStart}>
            <Play aria-hidden /> Arrivée au verger
          </Button>
        )}

        {!terminal && (
          <label className="flex flex-col gap-2" htmlFor={`skip-reason-${stop.order}`}>
            <span className="text-sm font-medium">Motif si l’arrêt est ignoré</span>
            <textarea
              id={`skip-reason-${stop.order}`}
              value={draft.comment}
              onChange={(event) => onChange({ comment: event.target.value })}
              className="min-h-24 w-full rounded-lg border border-input bg-transparent px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              placeholder="Verger inaccessible, météo…"
            />
          </label>
        )}

        {stop.status === 'IN_PROGRESS' && (
          <>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field
                id={`harvested-kg-${stop.order}`}
                label="Quantité récoltée (kg)"
                type="number"
                min="0"
                step="0.1"
                value={draft.harvestedKg}
                onChange={(event) => onChange({ harvestedKg: event.target.value })}
              />
              <Field
                id={`crate-count-${stop.order}`}
                label="Nombre de caisses"
                type="number"
                min="0"
                step="1"
                value={draft.crateCount}
                onChange={(event) => onChange({ crateCount: event.target.value })}
              />
            </div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <Button type="button" disabled={busy} onClick={onDone}>
                <Check aria-hidden /> Marquer terminé
              </Button>
              <Button type="button" variant="destructive" disabled={busy} onClick={onSkip}>
                <SkipForward aria-hidden /> Ignorer l’arrêt
              </Button>
            </div>
          </>
        )}

        {stop.status === 'PENDING' && (
          <Button type="button" variant="destructive" disabled={busy} onClick={onSkip}>
            <SkipForward aria-hidden /> Ignorer l’arrêt
          </Button>
        )}
        {stop.status === 'DONE' && <p className="text-sm">Récolte enregistrée : {stop.harvestedKg?.toLocaleString()} kg · {stop.crateCount} caisses.</p>}
        {stop.status === 'SKIPPED' && <p className="text-sm">Motif : {stop.comment || 'Aucun motif indiqué.'}</p>}
      </CardContent>
    </Card>
  )
}

function submitStop(
  roundId: string,
  stop: RoundStop,
  draft: StopDraft,
  status: 'DONE' | 'SKIPPED',
  runMutation: (mutation: RoundMutation) => Promise<void>,
  setError: (message: string | null) => void,
) {
  const result = buildStopUpdate(status, {
    harvestedKg: draft.harvestedKg,
    crateCount: draft.crateCount,
    skipReason: draft.comment,
  })
  if (!result.body) {
    setError(result.errors.join(' '))
    return
  }
  void runMutation(createRoundMutation({ roundId, action: 'STOP', order: stop.order, body: result.body }))
}

function applyOptimisticMutation(round: CollectionRound, mutation: RoundMutation): CollectionRound {
  if (mutation.action === 'START') return { ...round, status: 'IN_PROGRESS' }
  if (mutation.action === 'COMPLETE') return { ...round, status: 'COMPLETED' }
  return {
    ...round,
    stops: round.stops.map((stop) => stop.order === mutation.order
      ? { ...stop, status: mutation.body.status, harvestedKg: mutation.body.harvestedKg ?? null, crateCount: mutation.body.crateCount ?? null, comment: mutation.body.comment ?? null }
      : stop),
  }
}

function emptyStopDraft(): StopDraft {
  return { status: 'PENDING', harvestedKg: '', crateCount: '', comment: '' }
}

function mutationInfo(mutation: RoundMutation): string {
  if (mutation.action === 'START') return 'Tournée démarrée. Les agriculteurs concernés seront informés.'
  if (mutation.action === 'COMPLETE') return 'Tournée clôturée.'
  return mutation.body.status === 'SKIPPED' ? 'Arrêt ignoré.' : mutation.body.status === 'DONE' ? 'Récolte enregistrée.' : 'Arrêt démarré.'
}

function roundRunnerError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'La connexion au serveur a échoué. Réessayez ou continuez hors connexion.'
  if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) return error.errors.map(({ field, message }) => `${field}: ${message}`).join(' · ')
  if (error.code === 'ROUND_STARTED') return 'Cette tournée a déjà démarré et ne peut plus être modifiée de cette façon.'
  if (error.code === 'ROUND_NOT_PLANNED') return 'Cette tournée n’est plus planifiée.'
  return error.detail || 'La tournée n’a pas pu être mise à jour.'
}

function RoundStatusBadge({ status }: { status: CollectionRound['status'] }) {
  return <Badge variant={status === 'CANCELLED' ? 'destructive' : status === 'COMPLETED' ? 'default' : 'secondary'}>{ROUND_STATUS_LABEL[status]}</Badge>
}

function StopStatusBadge({ status }: { status: RoundStop['status'] }) {
  return <Badge variant={status === 'SKIPPED' ? 'destructive' : status === 'DONE' ? 'default' : 'secondary'}>{STOP_STATUS_LABEL[status]}</Badge>
}
