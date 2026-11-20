import { useState } from 'react'
import { createFileRoute } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  STATUS_LABEL,
  approveRegistration,
  listRegistrations,
  rejectRegistration,
  type Registration,
  type RegistrationStatus,
} from '@/admin/registrations'
import { RequireRole } from '@/components/RequireRole'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ApiError } from '@/lib/api'

export const Route = createFileRoute('/_app/admin/registrations')({ component: Page })

function Page() {
  return (
    <RequireRole role="ADMIN">
      <Registrations />
    </RequireRole>
  )
}

const STATUSES: RegistrationStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

function Registrations() {
  const [status, setStatus] = useState<RegistrationStatus>('PENDING')
  // The temporary password is returned once by the approve call: keep it here until dismissed.
  const [approved, setApproved] = useState<{ email: string; password: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['registrations', status], queryFn: () => listRegistrations(status) })

  const approve = useMutation({
    mutationFn: approveRegistration,
    onSuccess: (res) => setApproved({ email: res.email, password: res.temporaryPassword }),
  })
  const reject = useMutation({ mutationFn: rejectRegistration })

  async function act(run: () => Promise<unknown>) {
    setError(null)
    try {
      await run()
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Cannot reach the server.')
    }
    await queryClient.invalidateQueries({ queryKey: ['registrations'] })
  }

  const busy = approve.isPending || reject.isPending

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Registrations</h1>

      <div role="tablist" aria-label="Status" className="flex gap-2">
        {STATUSES.map((s) => (
          <Button
            key={s}
            role="tab"
            aria-selected={s === status}
            variant={s === status ? 'default' : 'outline'}
            className="h-11"
            onClick={() => setStatus(s)}
          >
            {STATUS_LABEL[s]}
          </Button>
        ))}
      </div>

      {approved && (
        <div role="status" className="rounded-lg border border-primary bg-muted p-4">
          <p>
            Account created for <strong>{approved.email}</strong>. Temporary password (shown only once):
          </p>
          <p className="my-2 break-all font-mono text-lg font-bold">{approved.password}</p>
          <p className="text-sm text-muted-foreground">
            Give it to the farmer. They must change it at their first login.
          </p>
          <Button variant="outline" className="mt-2 h-11" onClick={() => setApproved(null)}>
            Done
          </Button>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the registrations.
        </p>
      )}
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {query.data?.length === 0 && (
        <p className="text-muted-foreground">No {STATUS_LABEL[status].toLowerCase()} requests.</p>
      )}

      <ul className="flex flex-col gap-2">
        {query.data?.map((r) => (
          <RegistrationCard
            key={r.id}
            r={r}
            busy={busy}
            onApprove={() => act(() => approve.mutateAsync(r.id))}
            onReject={() => act(() => reject.mutateAsync(r.id))}
          />
        ))}
      </ul>
    </>
  )
}

function RegistrationCard({
  r,
  busy,
  onApprove,
  onReject,
}: {
  r: Registration
  busy: boolean
  onApprove: () => void
  onReject: () => void
}) {
  return (
    <li>
      <Card>
        <CardContent className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-2">
            <span className="font-medium">
              {r.firstName} {r.lastName}
            </span>
            <Badge variant={r.status === 'PENDING' ? 'secondary' : 'outline'}>{STATUS_LABEL[r.status]}</Badge>
          </div>
          <span className="break-all text-sm text-muted-foreground">{r.email}</span>
          <span className="text-sm text-muted-foreground">
            {[r.phone, r.cin && `CIN ${r.cin}`].filter(Boolean).join(' · ')}
          </span>
          {r.message && <p className="text-sm">{r.message}</p>}
          {r.status === 'PENDING' && (
            <div className="flex gap-2">
              <Button className="h-11" disabled={busy} onClick={onApprove}>
                Approve
              </Button>
              <Button variant="outline" className="h-11" disabled={busy} onClick={onReject}>
                Reject
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </li>
  )
}
