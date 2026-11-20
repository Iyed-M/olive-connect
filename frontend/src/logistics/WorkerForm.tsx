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
  const banner = save.error ? (error ? errorBanner(error) : 'Cannot reach the server.') : null

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <Field id="firstName" label="First name" error={errors.firstName?.message} {...register('firstName')} />
          <Field id="lastName" label="Last name" error={errors.lastName?.message} {...register('lastName')} />
          <Field id="cin" label="CIN" hint="8 digits" error={errors.cin?.message} {...register('cin')} />
          <Field id="phone" label="Phone (optional)" type="tel" hint="+216 followed by 8 digits" error={errors.phone?.message} {...register('phone')} />
          <Field id="skills" label="Skills" hint="Separated by commas, for example picking, driving" error={errors.skills?.message} {...register('skills')} />
          <Field id="dailyRate" label="Daily rate (TND)" type="number" error={errors.dailyRate?.message} {...register('dailyRate')} />
          <label className="flex min-h-11 items-center gap-3 text-sm">
            <input type="checkbox" className="size-5" {...register('active')} />
            Active
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
          Saved.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11" disabled={save.isPending}>
          {save.isPending ? 'Saving...' : worker ? 'Save' : 'Create worker'}
        </Button>
        <Button type="button" variant="outline" className="h-11" onClick={() => void navigate({ to: '/logistics/workers' })}>
          Back to list
        </Button>
        {worker && !confirmDelete && (
          <Button type="button" variant="destructive" className="h-11" onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        )}
      </div>
      {worker && confirmDelete && (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Delete this worker? This cannot be undone.</p>
          <div className="flex gap-2">
            <Button type="button" variant="destructive" className="h-11" disabled={remove.isPending} onClick={() => remove.mutate()}>
              Yes, delete
            </Button>
            <Button type="button" variant="outline" className="h-11" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {remove.error && (
        <p role="alert" className="text-sm text-destructive">
          {removeError ? errorBanner(removeError) : 'Cannot reach the server.'}
        </p>
      )}
    </form>
  )
}
