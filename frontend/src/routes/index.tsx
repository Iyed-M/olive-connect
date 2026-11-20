import { useQuery } from '@tanstack/react-query'
import { Link, createFileRoute } from '@tanstack/react-router'
import { useAuth } from '@/auth/AuthContext'
import { ROLE_HOME } from '@/auth/roles'
import { PublicPage } from '@/components/PublicPage'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { getSystemStatus, type Health } from '@/lib/api'

export const Route = createFileRoute('/')({ component: StatusPage })

function StatusBadge({ status }: { status: Health | 'LOADING' }) {
  if (status === 'LOADING') return <Badge variant="secondary">...</Badge>
  return <Badge variant={status === 'UP' ? 'default' : 'destructive'}>{status}</Badge>
}

function StatusPage() {
  const { data, isPending, isError, refetch, isFetching } = useQuery({
    queryKey: ['system-status'],
    queryFn: getSystemStatus,
    retry: false,
  })

  // API unreachable -> everything is considered DOWN.
  const rows: { label: string; status: Health | 'LOADING' }[] = [
    { label: 'API', status: isPending ? 'LOADING' : isError ? 'DOWN' : data.api },
    { label: 'MongoDB', status: isPending ? 'LOADING' : isError ? 'DOWN' : data.mongo },
    { label: 'Redis', status: isPending ? 'LOADING' : isError ? 'DOWN' : data.redis },
  ]

  const { activeRole } = useAuth()

  return (
    <PublicPage>
      <header>
        <h1 className="text-4xl font-bold tracking-tight text-primary">OliveConnect</h1>
        <p className="mt-2 text-muted-foreground">
          Smart olive collection for the cooperative, from farm to mill.
        </p>
        {activeRole ? (
          <Link to={ROLE_HOME[activeRole]} className={buttonVariants({ className: 'mt-4' })}>
            Go to my home
          </Link>
        ) : (
          <div className="mt-4 flex gap-2">
            <Link to="/login" className={buttonVariants()}>
              Log in
            </Link>
            <Link to="/register" className={buttonVariants({ variant: 'outline' })}>
              Join the cooperative
            </Link>
          </div>
        )}
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-xl">System status</CardTitle>
          <CardDescription>Live check of the backend services.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <ul className="divide-y divide-border">
            {rows.map((row) => (
              <li key={row.label} className="flex items-center justify-between py-3">
                <span className="font-medium">{row.label}</span>
                <StatusBadge status={row.status} />
              </li>
            ))}
          </ul>
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">
              Version{' '}
              <span className="font-mono tabular-nums text-foreground">
                {data?.version ?? '-'}
              </span>
            </span>
            <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
              Refresh
            </Button>
          </div>
          {isError && (
            <p role="alert" className="text-sm text-destructive">
              Cannot reach the API. Is the backend running?
            </p>
          )}
        </CardContent>
      </Card>
    </PublicPage>
  )
}
