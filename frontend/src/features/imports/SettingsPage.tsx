import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { setServerErrors } from '@/admin/users'
import { Field } from '@/components/Field'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { getSettings, parseNumber, updateSettings, type SystemSettings } from './imports'

interface SettingsValues {
  rainAlertMm24h: string
}

export function SettingsPage() {
  const query = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Settings</h1>
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the settings.
        </p>
      )}
      {query.data && <SettingsForm settings={query.data} />}
    </>
  )
}

function SettingsForm({ settings }: { settings: SystemSettings }) {
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<SettingsValues>({ defaultValues: { rainAlertMm24h: String(settings.rainAlertMm24h) } })

  const save = useMutation({
    mutationFn: (v: SettingsValues) => updateSettings({ ...settings, rainAlertMm24h: parseNumber(v.rainAlertMm24h) ?? 0 }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['settings'] })
      setSaved(true)
    },
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })

  const onSubmit = handleSubmit((values) => {
    setSaved(false)
    clearErrors()
    save.mutate(values)
  })

  const banner = !save.error
    ? null
    : save.error instanceof ApiError
      ? save.error.errors.length > 0
        ? null
        : save.error.detail
      : 'Cannot reach the server.'

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <Field
            id="rainAlertMm24h"
            label="Rain alert threshold (mm in 24 h)"
            type="number"
            hint="A rain alert is raised when the last 24 h reach this value"
            error={errors.rainAlertMm24h?.message}
            {...register('rainAlertMm24h', {
              validate: (value) => {
                const n = parseNumber(value)
                return n !== null && n > 0 ? true : 'Enter a number greater than 0'
              },
            })}
          />
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
      <Button type="submit" className="h-11 self-start" disabled={save.isPending}>
        {save.isPending ? 'Saving...' : 'Save'}
      </Button>
    </form>
  )
}
