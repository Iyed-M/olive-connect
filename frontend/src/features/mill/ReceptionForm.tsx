import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '@/lib/api'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { Label } from '@/components/ui/label'
import {
  buildReceptionPayload,
  calculateReceptionPreview,
  formatReceptionError,
  formatReceptionKg,
  formatReceptionRate,
  getBinsForStop,
  QUALITY_GRADE_LABEL,
  recordReception,
  type IncomingRound,
  type IncomingStop,
  type ReceptionFormValues,
  type QualityGrade,
} from './receptions'

export function ReceptionForm({ millId, round, stop }: { millId: string; round: IncomingRound; stop: IncomingStop }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const bins = getBinsForStop(round, stop)
  const {
    register,
    control,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<ReceptionFormValues>({
    defaultValues: {
      binId: bins[0]?.id ?? '',
      grossWeightKg: '',
      tareKg: '',
      quality: '',
    },
  })
  const selectedBinId = useWatch({ control, name: 'binId' })
  const grossWeightKg = useWatch({ control, name: 'grossWeightKg' })
  const tareKg = useWatch({ control, name: 'tareKg' })
  const selectedBin = bins.find((bin) => bin.id === selectedBinId)
  const preview = calculateReceptionPreview(grossWeightKg, tareKg, selectedBin?.capacityKg)

  const save = useMutation({
    mutationFn: recordReception.bind(null, millId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['mill-incoming', millId] })
      await queryClient.invalidateQueries({ queryKey: ['receptions'] })
      await navigate({ to: '/mill/incoming' })
    },
    onError: (error) => {
      if (!(error instanceof ApiError)) return
      for (const fieldError of error.errors) {
        if (fieldError.field === 'binId' || fieldError.field === 'grossWeightKg' || fieldError.field === 'tareKg' || fieldError.field === 'quality') {
          setError(fieldError.field, { type: 'server', message: fieldError.message })
        }
      }
    },
  })

  const onSubmit = handleSubmit((values) => {
    clearErrors()
    const result = buildReceptionPayload(values, round.id, stop.orchardId)
    for (const [field, message] of Object.entries(result.errors)) {
      if (message) setError(field as keyof ReceptionFormValues, { type: 'validate', message })
    }
    if (result.body) save.mutate(result.body)
  })

  const error = save.error ? formatReceptionError(save.error) : null

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div>
        <Link to="/mill/incoming" className="text-sm text-primary underline">← Retour aux tournées</Link>
        <h1 className="mt-2 font-heading text-2xl font-bold text-primary">Enregistrer une pesée</h1>
        <p className="text-sm text-muted-foreground">{round.code} · arrêt {stop.order}</p>
      </div>

      <Card>
        <CardHeader className="gap-1">
          <CardTitle>{stop.orchardName}</CardTitle>
          <p className="text-sm text-muted-foreground">Le verger et la tournée viennent de la file du jour et ne peuvent pas être modifiés ici.</p>
        </CardHeader>
        <CardContent className="flex flex-col gap-2 text-sm">
          <div className="flex flex-wrap gap-x-4 gap-y-1">
            <span><strong>Tournée :</strong> {round.code}</span>
            <span><strong>Arrivée :</strong> {stop.plannedArrival || '—'}</span>
          </div>
          <span><strong>Estimation récoltée :</strong> {formatReceptionKg(stop.harvestedKg ?? stop.estimatedKg)}</span>
        </CardContent>
      </Card>

      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Card>
          <CardHeader><CardTitle>Pesée et qualité</CardTitle></CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="binId">Benne</Label>
              <select id="binId" className={selectClass} aria-invalid={!!errors.binId} {...register('binId', { required: 'Choisissez une benne.' })}>
                <option value="">Choisissez une benne</option>
                {bins.map((bin) => <option key={bin.id} value={bin.id}>{bin.code}{bin.capacityKg ? ` · ${formatReceptionKg(bin.capacityKg)}` : ''}</option>)}
              </select>
              {errors.binId && <p className="text-sm text-destructive">{errors.binId.message}</p>}
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field id="grossWeightKg" label="Poids brut (kg)" type="number" min="0" step="0.1" error={errors.grossWeightKg?.message} {...register('grossWeightKg', { required: 'Saisissez le poids brut.' })} />
              <Field id="tareKg" label="Tare (kg)" type="number" min="0" step="0.1" error={errors.tareKg?.message} {...register('tareKg', { required: 'Saisissez la tare.' })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="quality">Qualité</Label>
              <select id="quality" className={selectClass} aria-invalid={!!errors.quality} {...register('quality', { required: 'Choisissez une qualité.' })}>
                <option value="">Choisissez une qualité</option>
                {(Object.keys(QUALITY_GRADE_LABEL) as QualityGrade[]).map((grade) => <option key={grade} value={grade}>{QUALITY_GRADE_LABEL[grade]}</option>)}
              </select>
              {errors.quality && <p className="text-sm text-destructive">{errors.quality.message}</p>}
            </div>
          </CardContent>
        </Card>

        <WeightPreview preview={preview} binCapacityKg={selectedBin?.capacityKg ?? null} />

        {bins.length === 0 && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Aucune benne de cette tournée n’est disponible dans les données reçues.</p>}
        {error && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" className="h-11" disabled={save.isPending || bins.length === 0}>{save.isPending ? 'Enregistrement…' : 'Enregistrer la réception'}</Button>
          <Link to="/mill/incoming" className={buttonVariants({ variant: 'outline', className: 'h-11' })}>Annuler</Link>
        </div>
      </form>
    </div>
  )
}

function WeightPreview({ preview, binCapacityKg }: { preview: ReturnType<typeof calculateReceptionPreview>; binCapacityKg: number | null }) {
  return (
    <Card>
      <CardHeader><CardTitle>Aperçu calculé</CardTitle></CardHeader>
      <CardContent className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
        <Metric label="Poids net" value={preview ? formatReceptionKg(preview.netWeightKg) : '—'} />
        <Metric label="Taux de remplissage" value={preview?.fillRate == null ? '—' : formatReceptionRate(preview.fillRate)} />
        <Metric label="Capacité de la benne" value={binCapacityKg == null ? '—' : formatReceptionKg(binCapacityKg)} />
        {preview?.fillRate != null && preview.fillRate > 1.15 && <p className="sm:col-span-3 rounded-lg bg-destructive/10 p-3 text-destructive">Le taux dépasse 115 %. Le serveur vérifiera la pesée avant de l’enregistrer.</p>}
        {preview?.netWeightKg != null && preview.netWeightKg <= 0 && <p className="sm:col-span-3 rounded-lg bg-destructive/10 p-3 text-destructive">Le poids net doit être positif.</p>}
      </CardContent>
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border bg-background p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="font-mono text-lg font-semibold">{value}</p></div>
}
