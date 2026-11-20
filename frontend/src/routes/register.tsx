import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, createFileRoute } from '@tanstack/react-router'
import { createRegistration, type RegistrationInput } from '@/admin/registrations'
import { errorBanner, setServerErrors } from '@/admin/users'
import { Field } from '@/components/Field'
import { PublicPage } from '@/components/PublicPage'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'

export const Route = createFileRoute('/register')({ component: RegisterPage })

const required = { required: 'Required' }

function RegisterPage() {
  const [sent, setSent] = useState(false)
  const [banner, setBanner] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegistrationInput>({
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', cin: '', message: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null)
    try {
      await createRegistration(values)
      setSent(true)
    } catch (err) {
      const error = err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Cannot reach the server.')
      if (error.code === 'VALIDATION_FAILED') setServerErrors(error.errors, setError)
      setBanner(errorBanner(error))
    }
  })

  if (sent) {
    return (
      <PublicPage>
        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-xl">Request sent</CardTitle>
            <CardDescription>
              Thank you. An administrator of the cooperative will review your request.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link to="/" className={buttonVariants({ variant: 'outline' })}>
              Back to home
            </Link>
          </CardContent>
        </Card>
      </PublicPage>
    )
  }

  return (
    <PublicPage>
      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-xl">Join the cooperative</CardTitle>
          <CardDescription>Send a registration request. An administrator will approve it.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            <Field id="firstName" label="First name" error={errors.firstName?.message} {...register('firstName', required)} />
            <Field id="lastName" label="Last name" error={errors.lastName?.message} {...register('lastName', required)} />
            <Field
              id="email"
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email', {
                required: 'Required',
                pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' },
              })}
            />
            <Field id="phone" label="Phone" type="tel" error={errors.phone?.message} {...register('phone', required)} />
            <Field
              id="cin"
              label="CIN"
              hint="National identity card number (8 digits)"
              error={errors.cin?.message}
              {...register('cin', { required: 'Required', pattern: { value: /^\d{8}$/, message: '8 digits' } })}
            />
            <div className="flex flex-col gap-2">
              <Label htmlFor="message">Message (optional)</Label>
              <textarea
                id="message"
                rows={3}
                maxLength={500}
                className="rounded-lg border border-input bg-background px-3 py-2 text-base md:text-sm"
                {...register('message')}
              />
              {errors.message && <p className="text-sm text-destructive">{errors.message.message}</p>}
            </div>
            {banner && (
              <p role="alert" className="text-sm text-destructive">
                {banner}
              </p>
            )}
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Sending...' : 'Send request'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </PublicPage>
  )
}
