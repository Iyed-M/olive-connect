import { useState, type FormEvent } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useAuth } from '@/auth/AuthContext'
import { homeFor } from '@/auth/home'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'

export const Route = createFileRoute('/login')({ component: LoginPage })

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      const user = await login(email, password)
      await navigate({ to: homeFor(user) })
    } catch (err) {
      // An unreachable API throws a TypeError: show it as a generic error.
      setError(err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Cannot reach the server.'))
      setPending(false)
    }
  }

  const fieldError = (field: string) => error?.errors.find((e) => e.field === field)?.message

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading text-xl">Log in</CardTitle>
        <CardDescription>Use the email and password given by your cooperative.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={!!fieldError('email')}
            />
            {fieldError('email') && <p className="text-sm text-destructive">{fieldError('email')}</p>}
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!fieldError('password')}
            />
            {fieldError('password') && (
              <p className="text-sm text-destructive">{fieldError('password')}</p>
            )}
          </div>
          {error && error.code !== 'VALIDATION_FAILED' && (
            <p role="alert" className="text-sm text-destructive">
              {error.code === 'INVALID_CREDENTIALS' ? 'Invalid email or password.' : error.detail}
            </p>
          )}
          <Button type="submit" disabled={pending}>
            {pending ? 'Logging in...' : 'Log in'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
