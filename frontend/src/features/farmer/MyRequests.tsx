import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { StatusBadge } from '@/components/StatusBadge'
import { Timeline } from '@/components/Timeline'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CancelRequestButton } from './RequestActions'
import { listHarvestRequests, requestHistory, type HarvestRequest } from './harvestRequests'

export function MyRequestsPage() {
  return <RequireRole role="FARMER"><MyRequestsList /></RequireRole>
}

function MyRequestsList() {
  const query = useQuery({
    queryKey: ['harvest-requests', { mine: true }],
    queryFn: () => listHarvestRequests({ mine: true, page: 0, size: 50 }),
  })
  const error = query.error instanceof ApiError ? query.error : null

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">My harvest requests</h1>
          <p className="text-sm text-muted-foreground">Follow each request from declaration to collection.</p>
        </div>
        <Link to="/farmer/requests/new" className={buttonVariants({ className: 'h-11' })}><Plus aria-hidden /> New</Link>
      </div>

      {query.isPending && <p className="text-muted-foreground">Loading requests...</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Could not load your harvest requests.'}</p>}
      {query.data?.content.length === 0 && (
        <Card><CardContent className="flex flex-col items-start gap-3 py-6">
          <p>No harvest requests yet. Declare one when your olives are ready.</p>
          <Link to="/farmer/requests/new" className={buttonVariants()}>Declare a harvest</Link>
        </CardContent></Card>
      )}
      {query.data && query.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {query.data.content.map((request) => <RequestCard key={request.id} request={request} />)}
          <Button variant="outline" className="w-fit" onClick={() => void query.refetch()} disabled={query.isFetching}>
            {query.isFetching ? 'Refreshing...' : 'Refresh'}
          </Button>
        </div>
      )}
    </div>
  )
}

function RequestCard({ request }: { request: HarvestRequest }) {
  const canModify = request.allowedActions.includes('MODIFY')
  const canCancel = request.allowedActions.includes('CANCEL')

  return (
    <Card>
      <CardHeader className="gap-2">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle><Link to="/farmer/requests/$id" params={{ id: request.id }} className="text-primary hover:underline">{request.orchard.name}</Link></CardTitle>
            <p className="text-xs text-muted-foreground">{displayDelegation(request.orchard.delegation)} · {formatDateRange(request.desiredFrom, request.desiredTo)}</p>
          </div>
          <StatusBadge status={request.status} />
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="rounded-full bg-secondary px-2 py-1">{request.type === 'URGENT' ? 'Urgent' : 'Planned'}</span>
          <span>{request.estimatedQuantityKg.toLocaleString()} kg</span>
          <span className="text-muted-foreground">Priority {request.priorityScore.toFixed(1)}</span>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {request.rejectionReason && <p className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Reason: {request.rejectionReason}</p>}
        <Timeline history={requestHistory(request)} />
        <div className="flex flex-wrap gap-2">
          <Link to="/farmer/requests/$id" params={{ id: request.id }} className="inline-flex h-9 items-center rounded-lg border px-3 text-sm">View details</Link>
          {canModify && <Link to="/farmer/requests/$id" params={{ id: request.id }} className="inline-flex h-9 items-center rounded-lg border px-3 text-sm">Modify</Link>}
          {canCancel && <CancelRequestButton request={request} />}
        </div>
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
