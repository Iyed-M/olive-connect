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

const required = { required: 'Obligatoire' }

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
      const error = err instanceof ApiError ? err : new ApiError(0, 'NETWORK', 'Impossible de joindre le serveur.')
      if (error.code === 'VALIDATION_FAILED') setServerErrors(error.errors, setError)
      setBanner(errorBanner(error))
    }
  })

  if (sent) {
    return (
      <PublicPage>
        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-xl">Demande envoyée</CardTitle>
            <CardDescription>
              Merci. Un administrateur de la coopérative examinera votre demande.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link to="/" className={buttonVariants({ variant: 'outline' })}>
              Retour à l’accueil
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
          <CardTitle className="font-heading text-xl">Rejoindre la coopérative</CardTitle>
          <CardDescription>Envoyez une demande d’inscription. Un administrateur la validera.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
            <Field id="firstName" label="Prénom" error={errors.firstName?.message} {...register('firstName', required)} />
            <Field id="lastName" label="Nom" error={errors.lastName?.message} {...register('lastName', required)} />
            <Field
              id="email"
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email', {
                required: 'Obligatoire',
                pattern: { value: /^\S+@\S+\.\S+$/, message: 'Saisissez un e-mail valide' },
              })}
            />
            <Field id="phone" label="Téléphone" type="tel" error={errors.phone?.message} {...register('phone', required)} />
            <Field
              id="cin"
              label="CIN"
              hint="Numéro de carte d’identité nationale (8 chiffres)"
              error={errors.cin?.message}
              {...register('cin', { required: 'Obligatoire', pattern: { value: /^\d{8}$/, message: '8 chiffres' } })}
            />
            <div className="flex flex-col gap-2">
              <Label htmlFor="message">Message (facultatif)</Label>
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
              {isSubmitting ? 'Envoi…' : 'Envoyer la demande'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </PublicPage>
  )
}
