import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '@/lib/api'
import { selectClass } from '@/components/Field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { listOrchards, type GeoPoint } from '@/features/farmer/orchards'
import {
  ALERT_TYPE_LABEL,
  alertErrorMessage,
  alertPhotoError,
  createAlert,
  findNearestOrchardLocally,
  getNearestOrchard,
  type AlertReportValues,
  type AlertType,
} from './alerts'

interface AlertFormValues {
  orchardId: string
  type: AlertType
  description: string
  photo: FileList | null
}

type LocationStatus = 'idle' | 'loading' | 'located' | 'denied' | 'unavailable'

export function AlertForm() {
  const queryClient = useQueryClient()
  const [location, setLocation] = useState<GeoPoint | null>(null)
  const [locationStatus, setLocationStatus] = useState<LocationStatus>(() => (
    typeof navigator === 'undefined' || !navigator.geolocation ? 'unavailable' : 'loading'
  ))
  const [locationError, setLocationError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const orchardsQuery = useQuery({ queryKey: ['orchards'], queryFn: listOrchards })
  const nearestQuery = useQuery({
    queryKey: ['nearest-orchard', location?.coordinates[0], location?.coordinates[1]],
    queryFn: () => getNearestOrchard(location!),
    enabled: locationStatus === 'located' && Boolean(location),
    retry: false,
  })
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    reset,
    formState: { errors },
  } = useForm<AlertFormValues>({
    defaultValues: { orchardId: '', type: 'MATURITY', description: '', photo: null },
  })
  const selectedOrchardId = useWatch({ control, name: 'orchardId' })
  const orchardField = register('orchardId', { required: 'Choisissez un verger.' })

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocation({ type: 'Point', coordinates: [coords.longitude, coords.latitude] })
        setLocationStatus('located')
      },
      () => setLocationStatus('denied'),
      { enableHighAccuracy: true, maximumAge: 300_000, timeout: 8_000 },
    )
  }, [])

  useEffect(() => {
    if (!location || locationStatus !== 'located' || selectedOrchardId || (!nearestQuery.isSuccess && !nearestQuery.isError)) return
    const nearest = nearestQuery.data
    if (nearest) {
      setValue('orchardId', nearest.id)
      return
    }
    const fallback = findNearestOrchardLocally(orchardsQuery.data ?? [], location)
    if (fallback) setValue('orchardId', fallback.id)
  }, [location, locationStatus, nearestQuery.data, nearestQuery.isError, nearestQuery.isSuccess, orchardsQuery.data, selectedOrchardId, setValue])

  const mutation = useMutation({
    mutationFn: (values: AlertReportValues) => createAlert(values),
    onSuccess: async () => {
      setNotice('Alerte envoyée. Le responsable de collecte sera prévenu.')
      reset()
      await queryClient.invalidateQueries({ queryKey: ['alerts'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  function chooseOrchard(orchardId: string) {
    setValue('orchardId', orchardId, { shouldValidate: true })
    if (locationStatus !== 'located') {
      const orchard = orchardsQuery.data?.find((candidate) => candidate.id === orchardId)
      if (orchard) setLocation(orchard.location)
    }
  }

  function submit(values: AlertFormValues) {
    setNotice(null)
    setLocationError(null)
    const photo = values.photo?.item(0) ?? null
    const photoError = alertPhotoError(photo)
    if (photoError) {
      setError('photo', { type: 'validate', message: photoError })
      return
    }
    const orchard = orchardsQuery.data?.find((candidate) => candidate.id === values.orchardId)
    const reportLocation = location ?? orchard?.location ?? null
    if (!reportLocation) {
      setLocationError('Choisissez un verger pour utiliser son emplacement comme position du signalement.')
      return
    }
    mutation.mutate({
      orchardId: values.orchardId,
      type: values.type,
      severity: 'MEDIUM',
      description: values.description,
      location: reportLocation,
      photo,
    })
  }

  const errorMessage = mutation.error instanceof ApiError ? mutation.error.detail : mutation.error ? alertErrorMessage(mutation.error) : null

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="font-heading text-2xl font-bold text-primary">Signaler un problème</h1>
        <p className="text-sm text-muted-foreground">Votre position aide l’équipe à vérifier rapidement le verger concerné.</p>
      </div>

      {notice && <p role="status" className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm text-primary">{notice}</p>}
      {errorMessage && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{errorMessage}</p>}

      <Card>
        <CardHeader><CardTitle>Nouvelle alerte</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(submit)} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="alert-orchard">Verger concerné</Label>
              <select
                id="alert-orchard"
                className={selectClass}
                value={selectedOrchardId}
                aria-invalid={!!errors.orchardId}
                disabled={orchardsQuery.isPending}
                {...orchardField}
                onChange={(event) => {
                  void orchardField.onChange(event)
                  chooseOrchard(event.target.value)
                }}
              >
                <option value="">Choisir un verger</option>
                {(orchardsQuery.data ?? []).map((orchard) => <option key={orchard.id} value={orchard.id}>{orchard.name}</option>)}
              </select>
              {errors.orchardId?.message && <p className="text-sm text-destructive">{errors.orchardId.message}</p>}
              {orchardsQuery.isError && <p role="alert" className="text-sm text-destructive">Impossible de charger vos vergers.</p>}
              {orchardsQuery.data?.length === 0 && <p className="text-sm text-muted-foreground">Ajoutez d’abord un verger avant de signaler une alerte.</p>}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="alert-type">Type de problème</Label>
              <select id="alert-type" className={selectClass} {...register('type', { required: true })}>
                {(Object.keys(ALERT_TYPE_LABEL) as AlertType[]).map((type) => <option key={type} value={type}>{ALERT_TYPE_LABEL[type]}</option>)}
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="alert-description">Description (facultative)</Label>
              <textarea
                id="alert-description"
                rows={4}
                placeholder="Décrivez ce que vous observez…"
                aria-invalid={!!errors.description}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
                {...register('description', { maxLength: { value: 1000, message: 'La description ne peut pas dépasser 1000 caractères.' } })}
              />
              {errors.description?.message && <p className="text-sm text-destructive">{errors.description.message}</p>}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="alert-photo">Photo (JPEG ou PNG, 5 Mo maximum)</Label>
              <input
                id="alert-photo"
                type="file"
                accept="image/jpeg,image/png"
                aria-invalid={!!errors.photo}
                className="min-h-11 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                {...register('photo', { validate: (files) => alertPhotoError(files?.item(0)) ?? true })}
              />
              {errors.photo?.message && <p className="text-sm text-destructive">{errors.photo.message}</p>}
            </div>

            <LocationNotice status={locationStatus} nearestLoading={nearestQuery.isFetching} />
            {locationError && <p role="alert" className="text-sm text-destructive">{locationError}</p>}
            <Button type="submit" size="lg" disabled={mutation.isPending || orchardsQuery.isPending || !orchardsQuery.data?.length}>
              {mutation.isPending ? 'Envoi…' : 'Envoyer l’alerte'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

function LocationNotice({ status, nearestLoading }: { status: LocationStatus; nearestLoading: boolean }) {
  if (status === 'loading') return <p className="rounded-lg bg-muted/50 p-3 text-sm text-muted-foreground">Recherche de votre position…</p>
  if (status === 'located') return <p className="rounded-lg bg-primary/5 p-3 text-sm text-primary">Position GPS utilisée{nearestLoading ? ' · recherche du verger le plus proche…' : ' · le verger le plus proche est présélectionné.'}</p>
  if (status === 'denied' || status === 'unavailable') return <p className="rounded-lg bg-muted/50 p-3 text-sm text-muted-foreground">Position indisponible : choisissez un verger. Sa position sera utilisée comme emplacement du signalement.</p>
  return <p className="rounded-lg bg-muted/50 p-3 text-sm text-muted-foreground">Choisissez un verger pour continuer.</p>
}
