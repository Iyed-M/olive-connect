import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { selectClass } from '@/components/Field'
import {
  listHarvestRequests,
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
        <h1 className="font-heading text-2xl font-bold text-primary">Harvest request queue</h1>
        <p className="text-sm text-muted-foreground">Review requests in priority order, then validate or reject them.</p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-2 text-sm font-medium">
          Status
          <select aria-label="Filter by request status" className={selectClass} value={status} onChange={(event) => setStatus(event.target.value as QueueStatus)}>
            <option value="SUBMITTED">Submitted</option>
            <option value="VALIDATED">Validated</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="">All requests</option>
          </select>
        </label>
        <label className="flex flex-col gap-2 text-sm font-medium">
          Type
          <select aria-label="Filter by request type" className={selectClass} value={type} onChange={(event) => setType(event.target.value as HarvestType | '')}>
            <option value="">Planned and urgent</option>
            <option value="URGENT">Urgent</option>
            <option value="PLANNED">Planned</option>
          </select>
        </label>
      </div>

      {query.isPending && <p className="text-muted-foreground">Loading the request queue...</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Could not load the request queue.'}</p>}
      {query.data && requests.length === 0 && <Card><CardContent className="py-6"><p>No requests match these filters.</p></CardContent></Card>}
      {requests.map((request) => <QueueCard key={request.id} request={request} />)}
      {query.data && <Button variant="outline" className="w-fit" onClick={() => void query.refetch()} disabled={query.isFetching}>{query.isFetching ? 'Refreshing...' : 'Refresh queue'}</Button>}
    </div>
  )
}

function QueueCard({ request }: { request: ReturnType<typeof sortRequestsByPriority>[number] }) {
  const queryClient = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)
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
      setReason('')
      setReasonError(null)
    },
  })
  const apiError = action.error

  function reject() {
    const trimmed = reason.trim()
    if (!trimmed) {
      setReasonError('A rejection reason is required.')
      return
    }
    setReasonError(null)
    action.mutate({ action: 'reject', reason: trimmed })
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
          <span className="rounded-full bg-secondary px-2 py-1">{request.type === 'URGENT' ? 'Urgent' : 'Planned'}</span>
          <span>{request.estimatedQuantityKg.toLocaleString()} kg</span>
          <strong className="text-primary">Priority {request.priorityScore.toFixed(1)}</strong>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {request.comment && <p className="text-sm text-muted-foreground">{request.comment}</p>}
        {request.status === 'SUBMITTED' && (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-2">
              <Button disabled={action.isPending} onClick={() => action.mutate({ action: 'validate' })}>Validate</Button>
              {!rejecting && <Button variant="destructive" disabled={action.isPending} onClick={() => setRejecting(true)}>Reject</Button>}
            </div>
            {rejecting && (
              <div className="flex flex-col gap-2 rounded-lg border border-destructive/40 p-3">
                <label htmlFor={`reject-${request.id}`} className="text-sm font-medium">Reason for rejection</label>
                <textarea
                  id={`reject-${request.id}`}
                  rows={3}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  aria-invalid={!!reasonError}
                  className="w-full rounded-lg border border-input px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
                />
                {reasonError && <p className="text-sm text-destructive">{reasonError}</p>}
                <div className="flex flex-wrap gap-2">
                  <Button variant="destructive" disabled={action.isPending} onClick={reject}>{action.isPending ? 'Rejecting...' : 'Confirm rejection'}</Button>
                  <Button variant="outline" disabled={action.isPending} onClick={() => { setRejecting(false); setReasonError(null) }}>Keep request</Button>
                </div>
              </div>
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
