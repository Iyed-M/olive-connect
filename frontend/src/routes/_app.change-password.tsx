import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { currentHome } from '@/auth/home'
import { getAccessToken, getUser, setSession } from '@/auth/session'
import { Field } from '@/components/Field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { setServerErrors } from '@/admin/users'
import { ApiError, api } from '@/lib/api'

// Under /_app (login needed). The guard in _app.tsx sends here a User with mustChangePassword.
export const Route = createFileRoute('/_app/change-password')({ component: ChangePasswordPage })

interface Values {
  currentPassword: string
  newPassword: string
  confirm: string
}

function ChangePasswordPage() {
  const navigate = useNavigate()
  const [banner, setBanner] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ defaultValues: { currentPassword: '', newPassword: '', confirm: '' } })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setBanner(null)
    try {
      await api<void>('/me/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) })
      const user = getUser()
      if (user) setSession(getAccessToken(), { ...user, mustChangePassword: false })
      await navigate({ to: currentHome() })
    } catch (err) {
      const error = err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Cannot reach the server.')
      if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) setServerErrors(error.errors, setError)
      else if (error.code === 'INVALID_CREDENTIALS') setError('currentPassword', { message: 'Wrong current password' })
      else setBanner(error.detail)
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading text-xl">Change your password</CardTitle>
        <CardDescription>
          {getUser()?.mustChangePassword
            ? 'You are using a temporary password. Choose a new one to continue.'
            : 'Choose a new password.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <Field
            id="currentPassword"
            label="Current password"
            type="password"
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register('currentPassword', { required: 'Required' })}
          />
          <Field
            id="newPassword"
            label="New password"
            type="password"
            autoComplete="new-password"
            hint="8 to 72 characters"
            error={errors.newPassword?.message}
            {...register('newPassword', {
              required: 'Required',
              minLength: { value: 8, message: 'At least 8 characters' },
              maxLength: { value: 72, message: 'At most 72 characters' },
            })}
          />
          <Field
            id="confirm"
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            error={errors.confirm?.message}
            {...register('confirm', {
              validate: (v) => v === getValues('newPassword') || 'Passwords do not match',
            })}
          />
          {banner && (
            <p role="alert" className="text-sm text-destructive">
              {banner}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Change password'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
