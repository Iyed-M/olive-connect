import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from '@tanstack/react-router'
import { ROLE_LABEL, ROLE_PRIORITY } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { Label } from '@/components/ui/label'
import {
  DELEGATION_LABEL,
  createUser,
  errorBanner,
  setServerErrors,
  setUserEnabled,
  updateUser,
  type Delegation,
  type UserDto,
  type UserWriteBody,
} from './users'
import { listMills } from '@/features/collection/planning'

// The values of the inputs. The farmer profile fields are only sent when the FARMER role is checked.
interface UserValues {
  email: string
  firstName: string
  lastName: string
  phone: string
  password: string
  roles: Role[]
  assignedMillId: string
  memberNumber: string
  cin: string
  delegation: Delegation | ''
  address: string
}

// One form for "new" (no `user`) and "edit".
export function UserForm({ user }: { user?: UserDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    setError,
    clearErrors,
    resetField,
    formState: { errors },
  } = useForm<UserValues>({
    defaultValues: {
      email: user?.email ?? '',
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      phone: user?.phone ?? '',
      password: '',
      roles: user?.roles ?? [],
      assignedMillId: user?.assignedMillId ?? '',
      memberNumber: user?.farmerProfile?.memberNumber ?? '',
      cin: user?.farmerProfile?.cin ?? '',
      delegation: user?.farmerProfile?.delegation ?? '',
      address: user?.farmerProfile?.address ?? '',
    },
  })
  const [confirmDisable, setConfirmDisable] = useState(false)
  const [saved, setSaved] = useState(false)

  const isFarmer = useWatch({ control, name: 'roles' }).includes('FARMER')
  const isMillManager = useWatch({ control, name: 'roles' }).includes('MILL_MANAGER')
  const millsQuery = useQuery({ queryKey: ['mills'], queryFn: listMills, enabled: isMillManager, staleTime: 30_000 })

  const save = useMutation({
    mutationFn: (v: UserValues) => {
      const body: UserWriteBody = {
        email: v.email,
        firstName: v.firstName,
        lastName: v.lastName,
        phone: v.phone.trim() || null,
        roles: v.roles,
        assignedMillId: isMillManager ? v.assignedMillId.trim() || null : null,
        // empty password on edit = keep the current one
        ...(v.password ? { password: v.password } : {}),
        farmerProfile: v.roles.includes('FARMER')
          ? {
              memberNumber: v.memberNumber,
              cin: v.cin,
              delegation: v.delegation || null,
              address: v.address.trim() || null,
            }
          : null,
      }
      return user ? updateUser(user.id, body) : createUser(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      if (user) {
        await queryClient.invalidateQueries({ queryKey: ['user', user.id] })
        resetField('password', { defaultValue: '' })
        setSaved(true)
      } else {
        await navigate({ to: '/admin/users' })
      }
    },
    // 400: each message goes on its field. 409 and 422: the banner below.
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })

  const toggle = useMutation({
    mutationFn: (enabled: boolean) => setUserEnabled(user!.id, enabled),
    onSuccess: async () => {
      setConfirmDisable(false)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      await queryClient.invalidateQueries({ queryKey: ['user', user!.id] })
    },
  })

  const onSubmit = handleSubmit((values) => {
    setSaved(false)
    clearErrors()
    save.mutate(values)
  })

  const error = save.error instanceof ApiError ? save.error : null
  const toggleError = toggle.error instanceof ApiError ? toggle.error : null
  const banner = save.error ? (error ? errorBanner(error) : 'Cannot reach the server.') : null

  return (
    <div className="flex flex-col gap-4">
      {user && (
        <Card>
          <CardContent className="flex flex-col gap-3">
            <p className="text-sm">
              Status: <strong>{user.enabled ? 'Enabled' : 'Disabled'}</strong>
            </p>
            {user.enabled && !confirmDisable && (
              <Button variant="destructive" className="h-11 w-fit" onClick={() => setConfirmDisable(true)}>
                Disable
              </Button>
            )}
            {user.enabled && confirmDisable && (
              <div className="flex flex-col gap-2">
                <p className="text-sm">Disable this user? All their sessions will be closed.</p>
                <div className="flex gap-2">
                  <Button variant="destructive" className="h-11" disabled={toggle.isPending} onClick={() => toggle.mutate(false)}>
                    Yes, disable
                  </Button>
                  <Button variant="outline" className="h-11" onClick={() => setConfirmDisable(false)}>
                    Cancel
                  </Button>
                </div>
              </div>
            )}
            {!user.enabled && (
              <Button className="h-11 w-fit" disabled={toggle.isPending} onClick={() => toggle.mutate(true)}>
                Enable
              </Button>
            )}
            {toggle.error && (
              <p role="alert" className="text-sm text-destructive">
                {toggleError ? errorBanner(toggleError) : 'Cannot reach the server.'}
              </p>
            )}
          </CardContent>
        </Card>
      )}

      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <Field id="email" label="Email" type="email" error={errors.email?.message} {...register('email')} />
            <Field id="firstName" label="First name" error={errors.firstName?.message} {...register('firstName')} />
            <Field id="lastName" label="Last name" error={errors.lastName?.message} {...register('lastName')} />
            <Field id="phone" label="Phone (optional)" type="tel" hint="+216 followed by 8 digits" error={errors.phone?.message} {...register('phone')} />
            <Field
              id="password"
              label={user ? 'New password (optional)' : 'Password'}
              type="password"
              autoComplete="new-password"
              hint={user ? 'Leave empty to keep the current one. A reset closes all sessions.' : '8 to 72 characters'}
              error={errors.password?.message}
              {...register('password')}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Roles</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-1">
            {ROLE_PRIORITY.map((role) => (
              <label key={role} className="flex min-h-11 items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="size-5"
                  value={role}
                  {...register('roles')}
                />
                {ROLE_LABEL[role]}
              </label>
            ))}
            {errors.roles && <p className="text-sm text-destructive">{errors.roles.message}</p>}
          </CardContent>
        </Card>

        {isFarmer && (
          <Card>
            <CardHeader>
              <CardTitle>Farmer profile</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <Field id="memberNumber" label="Member number" error={errors.memberNumber?.message} {...register('memberNumber')} />
              <Field id="cin" label="CIN" hint="8 digits" error={errors.cin?.message} {...register('cin')} />
              <div className="flex flex-col gap-2">
                <Label htmlFor="delegation">Delegation</Label>
                <select
                  id="delegation"
                  aria-invalid={!!errors.delegation}
                  className={selectClass}
                  {...register('delegation')}
                >
                  <option value="">Choose...</option>
                  {(Object.keys(DELEGATION_LABEL) as Delegation[]).map((d) => (
                    <option key={d} value={d}>
                      {DELEGATION_LABEL[d]}
                    </option>
                  ))}
                </select>
                {errors.delegation && <p className="text-sm text-destructive">{errors.delegation.message}</p>}
              </div>
              <Field id="address" label="Address (optional)" error={errors.address?.message} {...register('address')} />
            </CardContent>
          </Card>
        )}

        {isMillManager && (
          <Card>
            <CardHeader>
              <CardTitle>Mill assignment</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-2">
              <Label htmlFor="assignedMillId">Assigned mill</Label>
              <select
                id="assignedMillId"
                aria-invalid={!!errors.assignedMillId}
                className={selectClass}
                {...register('assignedMillId', { required: 'Choose a mill.' })}
              >
                <option value="">Choose...</option>
                {millsQuery.data?.map((mill) => (
                  <option key={mill.id} value={mill.id}>
                    {mill.name}
                  </option>
                ))}
                {user?.assignedMillId && !millsQuery.data?.some((mill) => mill.id === user.assignedMillId) && (
                  <option value={user.assignedMillId}>{user.assignedMillId}</option>
                )}
              </select>
              {errors.assignedMillId && <p className="text-sm text-destructive">{errors.assignedMillId.message}</p>}
              {millsQuery.isError && <p className="text-sm text-destructive">Unable to load mills.</p>}
            </CardContent>
          </Card>
        )}

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
        <div className="flex gap-2">
          <Button type="submit" className="h-11" disabled={save.isPending}>
            {save.isPending ? 'Saving...' : user ? 'Save' : 'Create user'}
          </Button>
          <Button type="button" variant="outline" className="h-11" onClick={() => void navigate({ to: '/admin/users' })}>
            Back to list
          </Button>
        </div>
      </form>
    </div>
  )
}
