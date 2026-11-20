import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { selectClass } from '@/components/Field'
import {
  listHarvestRequests,
  hasAllowedAction,
  rejectHarvestRequest,
  requestErrorMessage,
  sortRequestsByPriority,
  validateHarvestRequest,
  type HarvestRequestStatus,
  type HarvestType,
} from '@/features/farmer/harvestRequests'

type QueueStatus = '' | HarvestRequestStatus

export function RequestQueuePage() {
  return <RequireRole role="COLLECTION_MANAGER"><RequestQueue /></RequireRole>
}

function RequestQueue() {
  const [status, setStatus] = useState<QueueStatus>('SUBMITTED')
  const [type, setType] = useState<HarvestType | ''>('')
  const query = useQuery({
    queryKey: ['harvest-requests', { queue: true, status, type }],
    queryFn: () => listHarvestRequests({ status: status || undefined, type: type || undefined, page: 0, size: 50 }),
  })
  const requests = query.data ? sortRequestsByPriority(query.data.content) : []
  const error = query.error instanceof ApiError ? query.error : null

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="font-heading text-2xl font-bold text-primary">File des demandes de récolte</h1>
        <p className="text-sm text-muted-foreground">Examinez les demandes par priorité, puis validez-les ou refusez-les.</p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-2 text-sm font-medium">
          Statut
          <select aria-label="Filtrer par statut de demande" className={selectClass} value={status} onChange={(event) => setStatus(event.target.value as QueueStatus)}>
            <option value="SUBMITTED">Soumise</option>
            <option value="VALIDATED">Validée</option>
            <option value="REJECTED">Refusée</option>
            <option value="CANCELLED">Annulée</option>
            <option value="">Toutes les demandes</option>
          </select>
        </label>
        <label className="flex flex-col gap-2 text-sm font-medium">
          Type
          <select aria-label="Filtrer par type de demande" className={selectClass} value={type} onChange={(event) => setType(event.target.value as HarvestType | '')}>
            <option value="">Planifiées et urgentes</option>
            <option value="URGENT">Urgente</option>
            <option value="PLANNED">Planifiée</option>
          </select>
        </label>
      </div>

      {query.isPending && <p className="text-muted-foreground">Chargement de la file…</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Impossible de charger la file des demandes.'}</p>}
      {query.data && requests.length === 0 && <Card><CardContent className="py-6"><p>Aucune demande ne correspond à ces filtres.</p></CardContent></Card>}
      {requests.map((request) => <QueueCard key={request.id} request={request} />)}
      {query.data && <Button variant="outline" className="w-fit" onClick={() => void query.refetch()} disabled={query.isFetching}>{query.isFetching ? 'Actualisation…' : 'Actualiser la file'}</Button>}
    </div>
  )
}

function QueueCard({ request }: { request: ReturnType<typeof sortRequestsByPriority>[number] }) {
  const queryClient = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors: formErrors },
  } = useForm<{ reason: string }>({ defaultValues: { reason: '' } })
  const action = useMutation({
    mutationFn: (variables: { action: 'validate' | 'reject'; reason?: string }) =>
      variables.action === 'validate'
        ? validateHarvestRequest(request.id, request.version)
        : rejectHarvestRequest(request.id, request.version, variables.reason ?? ''),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['harvest-request', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['harvest-requests'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      setRejecting(false)
      reset()
    },
  })
  const apiError = action.error
  const canValidate = hasAllowedAction(request, 'VALIDATE')
  const canReject = hasAllowedAction(request, 'REJECT')

  function reject(values: { reason: string }) {
    action.mutate({ action: 'reject', reason: values.reason.trim() })
  }

  return (
    <Card>
      <CardHeader className="gap-2">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle>{request.orchard.name}</CardTitle>
            <p className="text-xs text-muted-foreground">{displayDelegation(request.orchard.delegation)} · {formatDateRange(request.desiredFrom, request.desiredTo)}</p>
          </div>
          <StatusBadge status={request.status} />
        </div>
        <div className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
          <span className="rounded-full bg-secondary px-2 py-1">{request.type === 'URGENT' ? 'Urgente' : 'Planifiée'}</span>
          <span>{request.estimatedQuantityKg.toLocaleString()} kg</span>
          <strong className="text-primary">Priorité {request.priorityScore.toFixed(1)}</strong>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {request.comment && <p className="text-sm text-muted-foreground">{request.comment}</p>}
        {(canValidate || canReject) && (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-2">
              {canValidate && <Button disabled={action.isPending} onClick={() => action.mutate({ action: 'validate' })}>Valider</Button>}
              {canReject && !rejecting && <Button variant="destructive" disabled={action.isPending} onClick={() => setRejecting(true)}>Refuser</Button>}
            </div>
            {rejecting && (
              <form onSubmit={handleSubmit(reject)} className="flex flex-col gap-2 rounded-lg border border-destructive/40 p-3">
                <label htmlFor={`reject-${request.id}`} className="text-sm font-medium">Motif du refus</label>
                <textarea
                  id={`reject-${request.id}`}
                  rows={3}
                  aria-invalid={!!formErrors.reason}
                  className="w-full rounded-lg border border-input px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
                  {...register('reason', { required: 'Le motif du refus est obligatoire.' })}
                />
                {formErrors.reason?.message && <p className="text-sm text-destructive">{formErrors.reason.message}</p>}
                <div className="flex flex-wrap gap-2">
                  <Button type="submit" variant="destructive" disabled={action.isPending}>{action.isPending ? 'Refus…' : 'Confirmer le refus'}</Button>
                  <Button type="button" variant="outline" disabled={action.isPending} onClick={() => { setRejecting(false); reset() }}>Conserver la demande</Button>
                </div>
              </form>
            )}
          </div>
        )}
        {apiError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{requestErrorMessage(apiError)}</p>}
      </CardContent>
    </Card>
  )
}

function formatDateRange(from: string, to: string): string {
  return `${formatDate(from)} – ${formatDate(to)}`
}

function formatDate(value: string): string {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

function displayDelegation(code: string): string {
  return code.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, (letter) => letter.toUpperCase())
}
