import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, fieldMessages } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toInstant, unavailabilityError, type Unavailability, type UnavailabilityBody } from './resources'

const fmt = (iso: string) => new Date(iso).toLocaleString()

// List of unavailabilities + the add form. Shared by the worker and the equipment detail pages.
// `add` calls the right endpoint; `onAdded` refreshes the resource.
export function UnavailabilityPanel({
  items,
  add,
  onAdded,
}: {
  items: Unavailability[]
  add: (body: UnavailabilityBody) => Promise<unknown>
  onAdded: () => Promise<unknown>
}) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [reason, setReason] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => add({ from: toInstant(from)!, to: toInstant(to)!, reason: reason.trim() || null }),
    onSuccess: async () => {
      setFrom('')
      setTo('')
      setReason('')
      await onAdded()
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    const error = unavailabilityError(from, to)
    setLocalError(error)
    if (!error) save.mutate()
  }

  const apiError = save.error instanceof ApiError ? save.error : null
  const fields = apiError ? fieldMessages(apiError.errors) : {}
  const banner = save.error ? (apiError ? errorBanner(apiError) : 'Cannot reach the server.') : null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Unavailabilities</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {items.length === 0 && <p className="text-sm text-muted-foreground">None yet.</p>}
        <ul className="flex flex-col gap-2">
          {items.map((u, i) => (
            <li key={i} className="rounded-md border p-2 text-sm">
              {fmt(u.from)} to {fmt(u.to)}
              {u.reason && <span className="text-muted-foreground"> · {u.reason}</span>}
            </li>
          ))}
        </ul>

        <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor="from">From</Label>
            <Input id="from" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} aria-invalid={!!fields.from} />
            {fields.from && <p className="text-sm text-destructive">{fields.from}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="to">To</Label>
            <Input id="to" type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} aria-invalid={!!fields.to} />
            {fields.to && <p className="text-sm text-destructive">{fields.to}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="reason">Reason (optional)</Label>
            <Input id="reason" value={reason} onChange={(e) => setReason(e.target.value)} />
            {fields.reason && <p className="text-sm text-destructive">{fields.reason}</p>}
          </div>
          {(localError || banner) && (
            <p role="alert" className="text-sm text-destructive">
              {localError ?? banner}
            </p>
          )}
          <Button type="submit" className="h-11 w-fit" disabled={save.isPending}>
            {save.isPending ? 'Adding...' : 'Add unavailability'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
