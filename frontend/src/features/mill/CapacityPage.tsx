import { useEffect } from 'react'
import { Link } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { RequireRole } from '@/components/RequireRole'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/Field'
import { listMills, type MillDto } from '@/features/collection/planning'
import { formatReceptionError, formatReceptionKg, getAssignedMillId, updateMillCapacity } from './receptions'

interface CapacityFormValues {
  availableCapacityKg: string
}

export function CapacityPage() {
  return (
    <RequireRole role="MILL_MANAGER">
      <CapacityEditor />
    </RequireRole>
  )
}

function CapacityEditor() {
  const millId = getAssignedMillId()
  const queryClient = useQueryClient()
  const millsQuery = useQuery({ queryKey: ['mills'], queryFn: listMills, enabled: Boolean(millId), staleTime: 30_000 })
  const mill = millsQuery.data?.find((candidate) => candidate.id === millId)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<CapacityFormValues>({ defaultValues: { availableCapacityKg: '' } })

  useEffect(() => {
    if (mill) reset({ availableCapacityKg: String(mill.availableCapacityKg) })
  }, [mill, reset])

  const save = useMutation({
    mutationFn: (availableCapacityKg: number) => updateMillCapacity(millId ?? '', availableCapacityKg),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['mills'] })
      await queryClient.invalidateQueries({ queryKey: ['mill-incoming', millId] })
    },
  })

  const onSubmit = handleSubmit((values) => {
    const capacity = Number(values.availableCapacityKg.trim())
    if (!Number.isFinite(capacity) || capacity < 0) {
      setError('availableCapacityKg', { type: 'validate', message: 'La capacité doit être un nombre positif ou nul.' })
      return
    }
    save.mutate(capacity)
  })

  const error = save.error ? formatReceptionError(save.error) : millsQuery.error ? formatReceptionError(millsQuery.error) : null

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div>
        <Link to="/mill/incoming" className="text-sm text-primary underline">← Retour aux réceptions</Link>
        <h1 className="mt-2 font-heading text-2xl font-bold text-primary">Capacité du moulin</h1>
        <p className="text-sm text-muted-foreground">Publiez la capacité disponible pour les prochaines réceptions.</p>
      </div>

      {!millId && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Aucun moulin n’est associé à votre session.</p>}
      {millsQuery.isPending && millId && <p className="text-muted-foreground">Chargement de la capacité…</p>}
      {millsQuery.isError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error ?? 'Impossible de charger le moulin.'}</p>}
      {millId && !millsQuery.isPending && !millsQuery.isError && !mill && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Le moulin associé à votre session est introuvable.</p>}
      {mill && <CapacityForm mill={mill} register={register} errors={errors} onSubmit={onSubmit} saving={save.isPending} saved={save.isSuccess} />}
      {save.error && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
    </div>
  )
}

function CapacityForm({
  mill,
  register,
  errors,
  onSubmit,
  saving,
  saved,
}: {
  mill: MillDto
  register: ReturnType<typeof useForm<CapacityFormValues>>['register']
  errors: ReturnType<typeof useForm<CapacityFormValues>>['formState']['errors']
  onSubmit: () => void
  saving: boolean
  saved: boolean
}) {
  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardHeader><CardTitle>{mill.name}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Metric label="Capacité journalière" value={formatReceptionKg(mill.dailyCapacityKg ?? 0)} />
            <Metric label="Disponible actuellement" value={formatReceptionKg(mill.availableCapacityKg)} />
          </div>
          <Field
            id="availableCapacityKg"
            label="Nouvelle capacité disponible (kg)"
            type="number"
            min="0"
            step="0.1"
            error={errors.availableCapacityKg?.message}
            {...register('availableCapacityKg', { required: 'Saisissez la capacité disponible.' })}
          />
        </CardContent>
      </Card>
      {saved && <p role="status" className="text-sm text-primary">Capacité publiée.</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11" disabled={saving}>{saving ? 'Publication…' : 'Publier la capacité'}</Button>
        <Link to="/mill/incoming" className={buttonVariants({ variant: 'outline', className: 'h-11' })}>Annuler</Link>
      </div>
    </form>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border bg-background p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="font-mono text-lg font-semibold">{value}</p></div>
}
