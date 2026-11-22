import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, createFileRoute, redirect, useRouter } from '@tanstack/react-router'
import { useAuth } from '@/auth/AuthContext'
import { currentHome } from '@/auth/home'
import { safeRedirect } from '@/auth/redirect'
import { PublicPage } from '@/components/PublicPage'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/Field'
import { setServerErrors } from '@/admin/users'
import { ApiError } from '@/lib/api'

// The message above the form for a login error that is not a field error.
function loginBanner(error: ApiError): string {
  if (error.code === 'INVALID_CREDENTIALS') return 'E-mail ou mot de passe incorrect.'
  if (error.code === 'TOO_MANY_ATTEMPTS') {
    return `Trop de tentatives. Réessayez dans ${Math.max(1, Math.ceil((error.retryAfterSeconds ?? 900) / 60))} min.`
  }
  return error.detail
}

export const Route = createFileRoute('/login')({
  // ?redirect=<path>: where to go after login (checked again by safeRedirect before use).
  validateSearch: (search: Record<string, unknown>): { redirect?: string } => ({
    redirect: typeof search.redirect === 'string' ? search.redirect : undefined,
  }),
  // A logged-in User has nothing to do here.
  beforeLoad: ({ context }) => {
    if (context.auth.isAuthenticated()) throw redirect({ to: currentHome() })
  },
  component: LoginPage,
})

interface LoginValues {
  email: string
  password: string
}

function LoginPage() {
  const { login } = useAuth()
  const router = useRouter()
  const { redirect: target } = Route.useSearch()
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ defaultValues: { email: '', password: '' } })
  // The message for errors that are not tied to a field (401, 429, network).
  const [banner, setBanner] = useState<string | null>(null)

  const onSubmit = handleSubmit(async ({ email, password }) => {
    clearErrors()
    setBanner(null)
    try {
      await login(email, password)
      // Back to the page the User wanted, else the Role home of their Active role.
      router.history.push(safeRedirect(target) ?? currentHome())
    } catch (err) {
      // An unreachable API throws a TypeError: show it as a generic error.
      const error = err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Impossible de joindre le serveur.')
      if (error.code === 'VALIDATION_FAILED') setServerErrors(error.errors, setError)
      else setBanner(loginBanner(error))
    }
  })

  return (
    <PublicPage>
      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-xl">Se connecter</CardTitle>
          <CardDescription>Utilisez l’e-mail et le mot de passe fournis par votre coopérative.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            <Field
              id="email"
              label="Email"
              type="email"
              autoComplete="username"
              error={errors.email?.message}
              {...register('email')}
            />
            <Field
              id="password"
              label="Mot de passe"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />
            {banner && (
              <p role="alert" className="text-sm text-destructive">
                {banner}
              </p>
            )}
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Connexion…' : 'Se connecter'}
            </Button>
          </form>
          <p className="mt-4 text-sm text-muted-foreground">
            Pas encore membre ?{' '}
            <Link to="/register" className="text-primary underline">
              Demander à rejoindre
            </Link>
          </p>
        </CardContent>
      </Card>
    </PublicPage>
  )
}
