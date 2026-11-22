import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, setServerErrors } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field } from '@/components/Field'
import {
  createWorker,
  deleteWorker,
  parseNumber,
  parseSkills,
  updateWorker,
  type WorkerDto,
  type WorkerWriteBody,
} from './resources'

// The values of the inputs. Numbers and skills stay text until the request body is built.
interface WorkerValues {
  firstName: string
  lastName: string
  cin: string
  phone: string
  skills: string
  dailyRate: string
  active: boolean
}

// One form for "new" (no `worker`) and "edit".
export function WorkerForm({ worker }: { worker?: WorkerDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<WorkerValues>({
    defaultValues: {
      firstName: worker?.firstName ?? '',
      lastName: worker?.lastName ?? '',
      cin: worker?.cin ?? '',
      phone: worker?.phone ?? '',
      skills: worker?.skills.join(', ') ?? '',
      dailyRate: worker ? String(worker.dailyRate) : '',
      active: worker?.active ?? true,
    },
  })
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: (v: WorkerValues) => {
      const body: WorkerWriteBody = {
        firstName: v.firstName,
        lastName: v.lastName,
        cin: v.cin,
        phone: v.phone.trim() || null,
        skills: parseSkills(v.skills),
        dailyRate: parseNumber(v.dailyRate),
        active: v.active,
      }
      return worker ? updateWorker(worker.id, body) : createWorker(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['workers'] })
      if (worker) {
        await queryClient.invalidateQueries({ queryKey: ['worker', worker.id] })
        setSaved(true)
      } else {
        await navigate({ to: '/logistics/workers' })
      }
    },
    // 400: each message goes on its field. 409 and 422: the banner below.
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })

  const remove = useMutation({
    mutationFn: () => deleteWorker(worker!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['workers'] })
      await navigate({ to: '/logistics/workers' })
    },
  })

  const onSubmit = handleSubmit((values) => {
    setSaved(false)
    clearErrors()
    save.mutate(values)
  })

  const error = save.error instanceof ApiError ? save.error : null
  const removeError = remove.error instanceof ApiError ? remove.error : null
  const banner = save.error ? (error ? errorBanner(error) : 'Impossible de joindre le serveur.') : null

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <Field id="firstName" label="Prénom" error={errors.firstName?.message} {...register('firstName')} />
          <Field id="lastName" label="Nom" error={errors.lastName?.message} {...register('lastName')} />
          <Field id="cin" label="CIN" hint="8 chiffres" error={errors.cin?.message} {...register('cin')} />
          <Field id="phone" label="Téléphone (facultatif)" type="tel" hint="+216 suivi de 8 chiffres" error={errors.phone?.message} {...register('phone')} />
          <Field id="skills" label="Compétences" hint="Séparées par des virgules, par exemple cueillette, conduite" error={errors.skills?.message} {...register('skills')} />
          <Field id="dailyRate" label="Tarif journalier (TND)" type="number" error={errors.dailyRate?.message} {...register('dailyRate')} />
          <label className="flex min-h-11 items-center gap-3 text-sm">
            <input type="checkbox" className="size-5" {...register('active')} />
            Actif
          </label>
        </CardContent>
      </Card>

      {banner && (
        <p role="alert" className="text-sm text-destructive">
          {banner}
        </p>
      )}
      {saved && !save.isPending && !save.error && (
        <p role="status" className="text-sm text-primary">
          Enregistré.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11" disabled={save.isPending}>
          {save.isPending ? 'Enregistrement…' : worker ? 'Enregistrer' : 'Créer l’ouvrier'}
        </Button>
        <Button type="button" variant="outline" className="h-11" onClick={() => void navigate({ to: '/logistics/workers' })}>
          Retour à la liste
        </Button>
        {worker && !confirmDelete && (
          <Button type="button" variant="destructive" className="h-11" onClick={() => setConfirmDelete(true)}>
            Supprimer
          </Button>
        )}
      </div>
      {worker && confirmDelete && (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Supprimer cet ouvrier ? Cette action est irréversible.</p>
          <div className="flex gap-2">
            <Button type="button" variant="destructive" className="h-11" disabled={remove.isPending} onClick={() => remove.mutate()}>
              Oui, supprimer
            </Button>
            <Button type="button" variant="outline" className="h-11" onClick={() => setConfirmDelete(false)}>
              Annuler
            </Button>
          </div>
        </div>
      )}
      {remove.error && (
        <p role="alert" className="text-sm text-destructive">
          {removeError ? errorBanner(removeError) : 'Impossible de joindre le serveur.'}
        </p>
      )}
    </form>
  )
}
