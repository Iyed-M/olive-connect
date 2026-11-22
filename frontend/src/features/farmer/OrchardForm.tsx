import { useEffect } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useFieldArray, useForm, useWatch, type Path } from 'react-hook-form'
import { Plus, Trash2 } from 'lucide-react'
import { ApiError, type FieldError as ApiFieldError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { Label } from '@/components/ui/label'
import { OrchardMap } from './OrchardMap'
import {
  createOrchard,
  deleteOrchard,
  listVarieties,
  updateOrchard,
  type GeoPoint,
  type Orchard,
  type OrchardWriteBody,
  type Plot,
} from './orchards'

const DEFAULT_LOCATION: GeoPoint = { type: 'Point', coordinates: [10.7603, 34.7998] }
const DEFAULT_PLOT: Plot = { code: 'P1', varietyCode: 'CHEMLALI', areaHa: 1, treeCount: 100, plantingYear: 2000, irrigated: false }
const DELEGATIONS = [
  'SFAX_VILLE',
  'SFAX_OUEST',
  'SFAX_SUD',
  'SAKIET_EZZIT',
  'SAKIET_EDDAIER',
  'THYNA',
  'AGAREB',
  'JEBINIANA',
  'EL_AMRA',
  'EL_HENCHA',
  'MENZEL_CHAKER',
  'GHRAIBA',
  'BIR_ALI_BEN_KHALIFA',
  'SKHIRA',
  'MAHRES',
  'KERKENNAH',
]

type OrchardFormValues = OrchardWriteBody

function displayDelegation(code: string) {
  return code.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, (letter) => letter.toUpperCase())
}

function initialPlots(orchard?: Orchard) {
  return orchard?.plots.length ? orchard.plots : [DEFAULT_PLOT]
}

function errorText(error: { message?: unknown } | undefined) {
  return typeof error?.message === 'string' ? error.message : undefined
}

function serverMessage(field: string, message: string) {
  return field === 'name' && (message === 'must not be blank' || message === 'is required')
    ? 'Le nom est obligatoire.'
    : message
}

function setServerErrors(
  errors: ApiFieldError[],
  setError: (field: Path<OrchardFormValues>, error: { type: string; message: string }) => void,
) {
  for (const { field, message } of errors) {
    const path = field.replace(/\[(\d+)\]/g, '.$1') as Path<OrchardFormValues>
    setError(path, { type: 'server', message: serverMessage(field, message) })
  }
}

// `staff`: a Logistics manager, Collection manager or Admin corrects someone else's orchard
// (FR-6.5): they go back to the intranet grid and cannot delete.
export function OrchardForm({ orchard, staff = false }: { orchard?: Orchard; staff?: boolean }) {
  const listPath = staff ? '/intranet/orchards' : '/farmer/orchards'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    clearErrors,
    formState: { errors },
  } = useForm<OrchardFormValues>({
    defaultValues: {
      name: orchard?.name ?? '',
      delegation: orchard?.delegation ?? 'SFAX_VILLE',
      location: orchard?.location ?? DEFAULT_LOCATION,
      plots: initialPlots(orchard),
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'plots' })
  const location = useWatch({ control, name: 'location' }) ?? DEFAULT_LOCATION
  const varieties = useQuery({ queryKey: ['varieties'], queryFn: listVarieties, staleTime: Infinity })

  const mutation = useMutation({
    mutationFn: (values: OrchardFormValues) => {
      const body: OrchardWriteBody = {
        ...values,
        name: values.name.trim(),
        plots: values.plots.map((plot) => ({
          ...plot,
          code: plot.code.trim(),
          varietyCode: plot.varietyCode.trim().toUpperCase(),
        })),
      }
      return orchard ? updateOrchard(orchard.id, body) : createOrchard(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['orchards'] })
      await queryClient.invalidateQueries({ queryKey: ['orchard'] })
      await navigate({ to: listPath })
    },
    onError: (error) => {
      if (error instanceof ApiError) setServerErrors(error.errors, setError)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: () => deleteOrchard(orchard!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['orchards'] })
      await navigate({ to: listPath })
    },
  })

  useEffect(() => {
    if (orchard || !navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => setValue('location', { type: 'Point', coordinates: [coords.longitude, coords.latitude] }, { shouldDirty: true }),
      () => setValue('location', DEFAULT_LOCATION),
      { enableHighAccuracy: true, timeout: 8000 },
    )
  }, [orchard, setValue])

  function save(values: OrchardFormValues) {
    clearErrors()
    mutation.mutate(values)
  }

  function addPlot() {
    append({ ...DEFAULT_PLOT, code: `P${fields.length + 1}` })
  }

  const error = mutation.error instanceof ApiError ? mutation.error : null
  const banner = mutation.isError && (!error || error.code !== 'VALIDATION_FAILED' || error.errors.length === 0)
    ? error?.detail ?? 'Impossible d’enregistrer le verger.'
    : null

  return (
    <form onSubmit={handleSubmit(save)} className="flex flex-col gap-4" noValidate>
      <div className="flex items-center justify-between gap-2">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">{orchard ? 'Modifier le verger' : 'Ajouter un verger'}</h1>
          <p className="text-sm text-muted-foreground">Choisissez la position, puis ajoutez chaque parcelle.</p>
        </div>
        <Link to={listPath} className="text-sm text-primary underline">Annuler</Link>
      </div>

      <Card>
        <CardHeader><CardTitle>Détails du verger</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Field
            id="name"
            label="Nom"
            error={errorText(errors.name)}
            maxLength={120}
            {...register('name', {
              required: 'Le nom est obligatoire.',
              validate: (value) => value.trim().length > 0 || 'Le nom est obligatoire.',
            })}
          />
          <div className="flex flex-col gap-2">
            <Label htmlFor="delegation">Délégation</Label>
            <select
              id="delegation"
              className={selectClass}
              aria-invalid={!!errorText(errors.delegation)}
              {...register('delegation', { required: 'La délégation est obligatoire.' })}
            >
              {DELEGATIONS.map((code) => <option key={code} value={code}>{displayDelegation(code)}</option>)}
            </select>
            {errorText(errors.delegation) && <p className="text-sm text-destructive">{errorText(errors.delegation)}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
              <Label>Position GPS</Label>
              <span className="font-mono text-xs text-muted-foreground">
                {location.coordinates[1].toFixed(5)}, {location.coordinates[0].toFixed(5)}
              </span>
            </div>
            <OrchardMap value={location} onChange={(point) => setValue('location', point, { shouldDirty: true })} />
            <p className="text-xs text-muted-foreground">Touchez la carte pour ajuster le repère.</p>
            {errorText(errors.location?.coordinates) && <p className="text-sm text-destructive">{errorText(errors.location?.coordinates)}</p>}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between gap-2">
          <CardTitle>Parcelles</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={addPlot}><Plus aria-hidden /> Ajouter une parcelle</Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {fields.map((field, index) => (
            <div key={field.id} className="flex flex-col gap-3 rounded-lg border p-3">
              <div className="flex items-center justify-between gap-2">
                <h2 className="font-medium">Parcelle {index + 1}</h2>
                <Button type="button" variant="ghost" size="icon" aria-label={`Supprimer la parcelle ${index + 1}`} disabled={fields.length === 1} onClick={() => remove(index)}>
                  <Trash2 aria-hidden />
                </Button>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Field
                  id={`plots.${index}.code`}
                  label="Code"
                  error={errorText(errors.plots?.[index]?.code)}
                  {...register(`plots.${index}.code`, { required: 'Le code de la parcelle est obligatoire.' })}
                />
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plots.${index}.varietyCode`}>Variété</Label>
                  <select
                    id={`plots.${index}.varietyCode`}
                    className={selectClass}
                    aria-invalid={!!errorText(errors.plots?.[index]?.varietyCode)}
                    {...register(`plots.${index}.varietyCode`, { required: 'La variété est obligatoire.' })}
                  >
                    {(varieties.data ?? []).map((variety) => <option key={variety.code} value={variety.code}>{variety.name}</option>)}
                  </select>
                  {errorText(errors.plots?.[index]?.varietyCode) && <p className="text-sm text-destructive">{errorText(errors.plots?.[index]?.varietyCode)}</p>}
                </div>
                <Field
                  id={`plots.${index}.areaHa`}
                  label="Surface (ha)"
                  type="number"
                  min="0.01"
                  step="0.01"
                  error={errorText(errors.plots?.[index]?.areaHa)}
                  {...register(`plots.${index}.areaHa`, {
                    valueAsNumber: true,
                    required: 'La surface est obligatoire.',
                    min: { value: 0.01, message: 'La surface doit être d’au moins 0,01 ha.' },
                  })}
                />
                <Field
                  id={`plots.${index}.treeCount`}
                  label="Arbres"
                  type="number"
                  min="1"
                  step="1"
                  error={errorText(errors.plots?.[index]?.treeCount)}
                  {...register(`plots.${index}.treeCount`, {
                    valueAsNumber: true,
                    required: 'Le nombre d’arbres est obligatoire.',
                    min: { value: 1, message: 'Le nombre d’arbres doit être d’au moins 1.' },
                  })}
                />
                <Field
                  id={`plots.${index}.plantingYear`}
                  label="Année de plantation"
                  type="number"
                  min="1800"
                  max="2100"
                  error={errorText(errors.plots?.[index]?.plantingYear)}
                  {...register(`plots.${index}.plantingYear`, {
                    valueAsNumber: true,
                    required: 'L’année de plantation est obligatoire.',
                    min: { value: 1800, message: 'L’année de plantation doit être comprise entre 1800 et 2100.' },
                    max: { value: 2100, message: 'L’année de plantation doit être comprise entre 1800 et 2100.' },
                  })}
                />
                <label className="flex min-h-11 items-center gap-3 text-sm">
                  <input type="checkbox" className="size-5" {...register(`plots.${index}.irrigated`)} />
                  Irrigué
                </label>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {banner && <p role="alert" className="text-sm text-destructive">{banner}</p>}
      {mutation.isError && !error && <p role="alert" className="text-sm text-destructive">Impossible d’enregistrer le verger.</p>}
      {deleteMutation.isError && <p role="alert" className="text-sm text-destructive">Impossible de supprimer le verger.</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Enregistrement…' : 'Enregistrer le verger'}</Button>
        <Link to={listPath} className="inline-flex h-11 items-center rounded-lg border px-4 text-sm">Retour aux vergers</Link>
        {orchard && !staff && <Button
          type="button"
          variant="destructive"
          disabled={deleteMutation.isPending}
          onClick={() => {
            if (window.confirm('Supprimer ce verger ?')) deleteMutation.mutate()
          }}
        >
          {deleteMutation.isPending ? 'Suppression…' : 'Supprimer'}
        </Button>}
      </div>
    </form>
  )
}
