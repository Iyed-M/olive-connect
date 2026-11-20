import { useEffect } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch, type Path } from 'react-hook-form'
import { ApiError, type FieldError as ApiFieldError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { Label } from '@/components/ui/label'
import { listOrchards } from './orchards'
import {
  createHarvestRequest,
  dateRangeError,
  estimateForOrchard,
  requestErrorMessage,
  updateHarvestRequest,
  type HarvestRequest,
  type HarvestRequestWriteBody,
  type HarvestType,
} from './harvestRequests'

interface HarvestRequestFormValues {
  orchardId: string
  type: HarvestType
  desiredFrom: string
  desiredTo: string
  estimatedQuantityKg: string
  comment: string
}

export function HarvestRequestForm({ request, onSaved }: { request?: HarvestRequest; onSaved?: (request: HarvestRequest) => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const today = localDate(new Date())
  const initialDates = defaultDateRange()
  const {
    register,
    control,
    getValues,
    handleSubmit,
    setError,
    setValue,
    clearErrors,
    formState: { errors },
  } = useForm<HarvestRequestFormValues>({
    defaultValues: {
      orchardId: request?.orchard.id ?? '',
      type: request?.type ?? 'PLANNED',
      desiredFrom: request?.desiredFrom ?? initialDates.from,
      desiredTo: request?.desiredTo ?? initialDates.to,
      estimatedQuantityKg: request ? String(request.estimatedQuantityKg) : '',
      comment: request?.comment ?? '',
    },
  })
  const orchards = useQuery({ queryKey: ['orchards'], queryFn: listOrchards, enabled: !request })
  const orchardId = useWatch({ control, name: 'orchardId' })
  const desiredFrom = useWatch({ control, name: 'desiredFrom' })
  const desiredTo = useWatch({ control, name: 'desiredTo' })
  const selectedOrchard = orchards.data?.find((orchard) => orchard.id === orchardId)

  useEffect(() => {
    if (request || !orchards.data?.length || getValues('orchardId')) return
    const firstOrchard = orchards.data[0]
    setValue('orchardId', firstOrchard.id)
    setValue('estimatedQuantityKg', String(estimateForOrchard(firstOrchard)))
  }, [getValues, orchards.data, request, setValue])

  useEffect(() => {
    if (request || !selectedOrchard) return
    setValue('estimatedQuantityKg', String(estimateForOrchard(selectedOrchard)), { shouldDirty: false })
  }, [request, selectedOrchard, setValue])

  const mutation = useMutation({
    mutationFn: (values: HarvestRequestFormValues) => {
      const estimatedQuantityKg = parseEstimatedQuantity(values.estimatedQuantityKg)
      const body: HarvestRequestWriteBody = {
        orchardId: values.orchardId,
        type: values.type,
        desiredFrom: values.desiredFrom,
        desiredTo: values.desiredTo,
        estimatedQuantityKg,
        comment: values.comment.trim() || null,
        ...(request ? { version: request.version } : {}),
      }
      return request ? updateHarvestRequest(request.id, body) : createHarvestRequest(body)
    },
    onSuccess: async (saved) => {
      queryClient.setQueryData(['harvest-request', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['harvest-requests'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      onSaved?.(saved)
      if (!request) await navigate({ to: '/farmer/requests/$id', params: { id: saved.id } })
    },
    onError: (error) => {
      if (error instanceof ApiError) setServerErrors(error.errors, setError)
    },
  })

  const apiError = mutation.error instanceof ApiError ? mutation.error : null
  const hasFieldErrors = !!apiError?.errors.length
  const banner = mutation.isError && (!hasFieldErrors || !apiError)
    ? requestErrorMessage(mutation.error)
    : null
  const dateError = errors.desiredFrom?.message ?? errors.desiredTo?.message

  function save(values: HarvestRequestFormValues) {
    clearErrors()
    mutation.mutate(values)
  }

  return (
    <form onSubmit={handleSubmit(save)} className="flex flex-col gap-4" noValidate>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">{request ? 'Modifier la demande de récolte' : 'Déclarer une récolte'}</h1>
          <p className="text-sm text-muted-foreground">Indiquez les dates et la quantité pour aider l’équipe de collecte à planifier.</p>
        </div>
        <Link to="/farmer/requests" className="inline-flex min-h-11 shrink-0 items-center text-sm text-primary underline">Retour</Link>
      </div>

      {banner && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{banner}</p>}
      {orchards.isError && !request && <p role="alert" className="text-sm text-destructive">Impossible de charger vos vergers. Réessayez avant de déclarer une récolte.</p>}

      <Card>
        <CardHeader><CardTitle>Détails de la récolte</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          {request ? (
            <div className="flex flex-col gap-2">
              <Label>Verger</Label>
              <p className="rounded-lg border bg-muted/40 px-3 py-2 text-sm">{request.orchard.name} · {displayDelegation(request.orchard.delegation)}</p>
              <p className="text-xs text-muted-foreground">Le verger ne peut plus être changé après la déclaration.</p>
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              <Label htmlFor="orchardId">Verger</Label>
              <select
                id="orchardId"
                className={selectClass}
                disabled={orchards.isPending || !orchards.data?.length}
                aria-invalid={!!errors.orchardId}
                {...register('orchardId', { required: 'Choisissez un verger.' })}
              >
                <option value="">{orchards.isPending ? 'Chargement des vergers…' : 'Choisissez un verger'}</option>
                {(orchards.data ?? []).map((orchard) => <option key={orchard.id} value={orchard.id}>{orchard.name}</option>)}
              </select>
              {errors.orchardId?.message && <p className="text-sm text-destructive">{errors.orchardId.message}</p>}
              {orchards.data?.length === 0 && <p className="text-sm text-muted-foreground">Ajoutez un verger avant de déclarer une récolte.</p>}
            </div>
          )}

          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-medium">Type de demande</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <Label htmlFor="type-planned" className="min-h-11 rounded-lg border px-3 text-sm">
                <input id="type-planned" type="radio" value="PLANNED" aria-invalid={!!errors.type}
                  {...register('type', { required: 'Choisissez un type de demande.' })} />
                Planifiée
              </Label>
              <Label htmlFor="type-urgent" className="min-h-11 rounded-lg border px-3 text-sm">
                <input id="type-urgent" type="radio" value="URGENT" aria-invalid={!!errors.type}
                  {...register('type', { required: 'Choisissez un type de demande.' })} />
                Urgente
              </Label>
            </div>
            {errors.type?.message && <p className="text-sm text-destructive">{errors.type.message}</p>}
          </fieldset>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field
              id="desiredFrom"
              label="Date la plus proche"
              type="date"
              min={today}
              error={errors.desiredFrom?.message}
              {...register('desiredFrom', {
                required: 'Choisissez la date la plus proche.',
                validate: (value) => dateRangeError(value, desiredTo, today) ?? true,
              })}
            />
            <Field
              id="desiredTo"
              label="Date la plus tardive"
              type="date"
              min={today}
              error={errors.desiredTo?.message}
              {...register('desiredTo', {
                required: 'Choisissez la date la plus tardive.',
                validate: (value) => dateRangeError(desiredFrom, value, today) ?? true,
              })}
            />
          </div>
          {dateError && <p className="-mt-2 text-sm text-destructive">{dateError}</p>}

          <Field
            id="estimatedQuantityKg"
            label="Quantité estimée (kg)"
            type="text"
            inputMode="decimal"
            hint={selectedOrchard ? `Suggestion calculée à partir de la surface et de la dernière récolte de ${selectedOrchard.name}.` : 'Saisissez la quantité d’olives attendue en kilogrammes.'}
            error={errorText(errors.estimatedQuantityKg)}
            {...register('estimatedQuantityKg', {
              validate: (value) => parseEstimatedQuantity(value) !== null || 'La quantité doit être supérieure à 0.',
            })}
          />

          <div className="flex flex-col gap-2">
            <Label htmlFor="comment">Commentaire <span className="font-normal text-muted-foreground">(facultatif)</span></Label>
            <textarea
              id="comment"
              rows={3}
              maxLength={500}
              className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
              aria-invalid={!!errors.comment}
              {...register('comment')}
            />
            {errorText(errors.comment) && <p className="text-sm text-destructive">{errorText(errors.comment)}</p>}
          </div>
        </CardContent>
      </Card>

      <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Link to="/farmer/requests" className="inline-flex h-11 items-center justify-center rounded-lg border px-4 text-sm">Annuler</Link>
        <Button type="submit" disabled={mutation.isPending || orchards.isPending}>
          {mutation.isPending ? 'Enregistrement…' : request ? 'Enregistrer les changements' : 'Envoyer la demande'}
        </Button>
      </div>
    </form>
  )
}

function setServerErrors(
  errors: ApiFieldError[],
  setError: (field: Path<HarvestRequestFormValues>, error: { type: string; message: string }) => void,
) {
  for (const { field, message } of errors) {
    if (field === 'desiredFrom' || field === 'desiredTo' || field === 'orchardId' || field === 'type' || field === 'estimatedQuantityKg' || field === 'comment') {
      setError(field, { type: 'server', message })
    }
  }
}

function errorText(error: { message?: unknown } | undefined): string | undefined {
  return typeof error?.message === 'string' ? error.message : undefined
}

function parseEstimatedQuantity(value: string): number | null {
  const parsed = Number(value.trim().replace(',', '.'))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function defaultDateRange() {
  const from = new Date()
  from.setDate(from.getDate() + 3)
  const to = new Date(from)
  to.setDate(to.getDate() + 3)
  return { from: localDate(from), to: localDate(to) }
}

function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function displayDelegation(code: string): string {
  return code.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, (letter) => letter.toUpperCase())
}
