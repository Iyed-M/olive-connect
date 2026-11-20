import { useState, type FormEvent } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, fieldMessages } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field } from './Field'
import {
  createWorker,
  deleteWorker,
  parseNumber,
  parseSkills,
  updateWorker,
  type WorkerDto,
  type WorkerWriteBody,
} from './resources'

// One form for "new" (no `worker`) and "edit".
export function WorkerForm({ worker }: { worker?: WorkerDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [firstName, setFirstName] = useState(worker?.firstName ?? '')
  const [lastName, setLastName] = useState(worker?.lastName ?? '')
  const [cin, setCin] = useState(worker?.cin ?? '')
  const [phone, setPhone] = useState(worker?.phone ?? '')
  const [skills, setSkills] = useState(worker?.skills.join(', ') ?? '')
  const [dailyRate, setDailyRate] = useState(worker ? String(worker.dailyRate) : '')
  const [active, setActive] = useState(worker?.active ?? true)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: () => {
      const body: WorkerWriteBody = {
        firstName,
        lastName,
        cin,
        phone: phone.trim() || null,
        skills: parseSkills(skills),
        dailyRate: parseNumber(dailyRate),
        active,
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
  })

  const remove = useMutation({
    mutationFn: () => deleteWorker(worker!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['workers'] })
      await navigate({ to: '/logistics/workers' })
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSaved(false)
    save.mutate()
  }

  const error = save.error instanceof ApiError ? save.error : null
  const removeError = remove.error instanceof ApiError ? remove.error : null
  const fields = error ? fieldMessages(error.errors) : {}
  const banner = save.error ? (error ? errorBanner(error) : 'Cannot reach the server.') : null

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <Field id="firstName" label="First name" value={firstName} onChange={setFirstName} error={fields.firstName} />
          <Field id="lastName" label="Last name" value={lastName} onChange={setLastName} error={fields.lastName} />
          <Field id="cin" label="CIN" value={cin} onChange={setCin} error={fields.cin} hint="8 digits" />
          <Field id="phone" label="Phone (optional)" type="tel" value={phone} onChange={setPhone} error={fields.phone} hint="+216 followed by 8 digits" />
          <Field id="skills" label="Skills" value={skills} onChange={setSkills} error={fields.skills} hint="Separated by commas, for example picking, driving" />
          <Field id="dailyRate" label="Daily rate (TND)" type="number" value={dailyRate} onChange={setDailyRate} error={fields.dailyRate} />
          <label className="flex min-h-11 items-center gap-3 text-sm">
            <input type="checkbox" className="size-5" checked={active} onChange={(e) => setActive(e.target.checked)} />
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
