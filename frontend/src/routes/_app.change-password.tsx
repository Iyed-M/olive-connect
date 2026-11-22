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
      await api<void>('/me/password', { method: 'PATCH', body: JSON.stringify({ currentPassword, newPassword }) })
      const user = getUser()
      if (user) setSession(getAccessToken(), { ...user, mustChangePassword: false })
      await navigate({ to: currentHome() })
    } catch (err) {
      const error = err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Impossible de joindre le serveur.')
      if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) setServerErrors(error.errors, setError)
      else if (error.code === 'WRONG_PASSWORD') setError('currentPassword', { message: 'Mot de passe actuel incorrect' })
      else setBanner(error.detail)
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading text-xl">Changez votre mot de passe</CardTitle>
        <CardDescription>
          {getUser()?.mustChangePassword
            ? 'Vous utilisez un mot de passe temporaire. Choisissez-en un nouveau pour continuer.'
            : 'Choisissez un nouveau mot de passe.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <Field
            id="currentPassword"
            label="Mot de passe actuel"
            type="password"
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register('currentPassword', { required: 'Obligatoire' })}
          />
          <Field
            id="newPassword"
            label="Nouveau mot de passe"
            type="password"
            autoComplete="new-password"
            hint="De 8 à 72 caractères"
            error={errors.newPassword?.message}
            {...register('newPassword', {
              required: 'Obligatoire',
              minLength: { value: 8, message: 'Au moins 8 caractères' },
              maxLength: { value: 72, message: 'Au plus 72 caractères' },
            })}
          />
          <Field
            id="confirm"
            label="Confirmer le nouveau mot de passe"
            type="password"
            autoComplete="new-password"
            error={errors.confirm?.message}
            {...register('confirm', {
              validate: (v) => v === getValues('newPassword') || 'Les mots de passe ne correspondent pas',
            })}
          />
          {banner && (
            <p role="alert" className="text-sm text-destructive">
              {banner}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Enregistrement…' : 'Changer le mot de passe'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
