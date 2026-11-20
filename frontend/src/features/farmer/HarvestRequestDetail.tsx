import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { StatusBadge } from '@/components/StatusBadge'
import { Timeline } from '@/components/Timeline'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { HarvestRequestForm } from './HarvestRequestForm'
import { CancelRequestButton } from './RequestActions'
import { getHarvestRequest, requestHistory, type HarvestRequest } from './harvestRequests'

export function HarvestRequestDetailPage({ id }: { id: string }) {
  return <RequireRole role="FARMER"><HarvestRequestDetail id={id} /></RequireRole>
}

function HarvestRequestDetail({ id }: { id: string }) {
  const [editing, setEditing] = useState(false)
  const query = useQuery({ queryKey: ['harvest-request', id], queryFn: () => getHarvestRequest(id) })
  const error = query.error instanceof ApiError ? query.error : null

  if (query.isPending) return <p className="text-muted-foreground">Loading request...</p>
  if (query.isError || !query.data) return <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Could not load this request.'}</p>
  if (editing) return <HarvestRequestForm request={query.data} onSaved={() => setEditing(false)} />

  return <RequestDetail request={query.data} onEdit={() => setEditing(true)} />
}

function RequestDetail({ request, onEdit }: { request: HarvestRequest; onEdit: () => void }) {
  const canModify = request.allowedActions.includes('MODIFY')
  const canCancel = request.allowedActions.includes('CANCEL')

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Harvest request</h1>
          <p className="text-sm text-muted-foreground">{request.orchard.name}</p>
        </div>
        <Link to="/farmer/requests" className="shrink-0 text-sm text-primary underline">Back</Link>
      </div>

      <Card>
        <CardHeader className="gap-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <CardTitle>{request.type === 'URGENT' ? 'Urgent harvest' : 'Planned harvest'}</CardTitle>
            <StatusBadge status={request.status} />
          </div>
          <p className="text-sm text-muted-foreground">{formatDateRange(request.desiredFrom, request.desiredTo)} · {request.estimatedQuantityKg.toLocaleString()} kg</p>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {request.comment && <p className="text-sm">{request.comment}</p>}
          {request.rejectionReason && <p className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Reason: {request.rejectionReason}</p>}
          <div>
            <h2 className="mb-3 font-heading text-lg font-semibold">Status timeline</h2>
            <Timeline history={requestHistory(request)} />
          </div>
          {(canModify || canCancel) && (
            <div className="flex flex-wrap gap-2 border-t pt-4">
              {canModify && <Button variant="outline" onClick={onEdit}>Modify request</Button>}
              {canCancel && <CancelRequestButton request={request} />}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
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
