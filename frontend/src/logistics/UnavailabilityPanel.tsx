import { useForm } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, setServerErrors } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toInstant, unavailabilityError, type Unavailability, type UnavailabilityBody } from './resources'

// The values of the inputs: `datetime-local` texts, converted to instants on submit.
interface UnavailabilityValues {
  from: string
  to: string
  reason: string
}

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
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    reset,
    formState: { errors },
  } = useForm<UnavailabilityValues>({ defaultValues: { from: '', to: '', reason: '' } })

  const save = useMutation({
    mutationFn: (v: UnavailabilityValues) =>
      add({ from: toInstant(v.from)!, to: toInstant(v.to)!, reason: v.reason.trim() || null }),
    onSuccess: async () => {
      reset()
      await onAdded()
    },
    // 400: each message goes on its field. 422: the banner below.
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })

  const onSubmit = handleSubmit((values) => {
    clearErrors()
    save.mutate(values)
  })

  const apiError = save.error instanceof ApiError ? save.error : null
  const banner = save.error ? (apiError ? errorBanner(apiError) : 'Impossible de joindre le serveur.') : null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Indisponibilités</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {items.length === 0 && <p className="text-sm text-muted-foreground">Aucune pour le moment.</p>}
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
            <Label htmlFor="from">Du</Label>
            <Input
              id="from"
              type="datetime-local"
              aria-invalid={!!errors.from}
              {...register('from', { required: 'Choisissez un début.' })}
            />
            {errors.from && <p className="text-sm text-destructive">{errors.from.message}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="to">Au</Label>
            <Input
              id="to"
              type="datetime-local"
              aria-invalid={!!errors.to}
              {...register('to', {
                required: 'Choisissez une fin.',
                // the end must be after the start (the server checks again, 422)
                validate: (to, { from }) => (from && unavailabilityError(from, to)) || true,
              })}
            />
            {errors.to && <p className="text-sm text-destructive">{errors.to.message}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="reason">Motif (facultatif)</Label>
            <Input id="reason" aria-invalid={!!errors.reason} {...register('reason')} />
            {errors.reason && <p className="text-sm text-destructive">{errors.reason.message}</p>}
          </div>
          {banner && (
            <p role="alert" className="text-sm text-destructive">
              {banner}
            </p>
          )}
          <Button type="submit" className="h-11 w-fit" disabled={save.isPending}>
            {save.isPending ? 'Ajout…' : 'Ajouter une indisponibilité'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
