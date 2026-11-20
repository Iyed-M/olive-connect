import { useState, type FormEvent } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ROLE_LABEL, ROLE_PRIORITY } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  DELEGATION_LABEL,
  createUser,
  errorBanner,
  fieldMessages,
  setUserEnabled,
  updateUser,
  type Delegation,
  type UserDto,
  type UserWriteBody,
} from './users'

// One form for "new" (no `user`) and "edit".
export function UserForm({ user }: { user?: UserDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState(user?.email ?? '')
  const [firstName, setFirstName] = useState(user?.firstName ?? '')
  const [lastName, setLastName] = useState(user?.lastName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [password, setPassword] = useState('')
  const [roles, setRoles] = useState<Role[]>(user?.roles ?? [])
  const [memberNumber, setMemberNumber] = useState(user?.farmerProfile?.memberNumber ?? '')
  const [cin, setCin] = useState(user?.farmerProfile?.cin ?? '')
  const [delegation, setDelegation] = useState<Delegation | null>(user?.farmerProfile?.delegation ?? null)
  const [address, setAddress] = useState(user?.farmerProfile?.address ?? '')
  const [confirmDisable, setConfirmDisable] = useState(false)
  const [saved, setSaved] = useState(false)

  const isFarmer = roles.includes('FARMER')

  const save = useMutation({
    mutationFn: () => {
      const body: UserWriteBody = {
        email,
        firstName,
        lastName,
        phone: phone.trim() || null,
        roles,
        // empty password on edit = keep the current one
        ...(password ? { password } : {}),
        farmerProfile: isFarmer
          ? { memberNumber, cin, delegation, address: address.trim() || null }
          : null,
      }
      return user ? updateUser(user.id, body) : createUser(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      if (user) {
        await queryClient.invalidateQueries({ queryKey: ['user', user.id] })
        setPassword('')
        setSaved(true)
      } else {
        await navigate({ to: '/admin/users' })
      }
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

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSaved(false)
    save.mutate()
  }

  function toggleRole(role: Role, on: boolean) {
    setRoles(on ? [...roles, role] : roles.filter((r) => r !== role))
  }

  const error = save.error instanceof ApiError ? save.error : null
  const toggleError = toggle.error instanceof ApiError ? toggle.error : null
  const fields = error ? fieldMessages(error.errors) : {}
  const banner = save.error ? (error ? errorBanner(error) : 'Cannot reach the server.') : null

  function text(
    id: string,
    label: string,
    value: string,
    set: (v: string) => void,
    props: { type?: string; autoComplete?: string; hint?: string } = {},
  ) {
    return (
      <div className="flex flex-col gap-2">
        <Label htmlFor={id}>{label}</Label>
        <Input
          id={id}
          type={props.type}
          autoComplete={props.autoComplete ?? 'off'}
          value={value}
          onChange={(e) => set(e.target.value)}
          aria-invalid={!!fields[id]}
        />
        {props.hint && !fields[id] && <p className="text-sm text-muted-foreground">{props.hint}</p>}
        {fields[id] && <p className="text-sm text-destructive">{fields[id]}</p>}
      </div>
    )
  }

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
            {text('email', 'Email', email, setEmail, { type: 'email' })}
            {text('firstName', 'First name', firstName, setFirstName)}
            {text('lastName', 'Last name', lastName, setLastName)}
            {text('phone', 'Phone (optional)', phone, setPhone, { type: 'tel', hint: '+216 followed by 8 digits' })}
            {text('password', user ? 'New password (optional)' : 'Password', password, setPassword, {
              type: 'password',
              autoComplete: 'new-password',
              hint: user ? 'Leave empty to keep the current one. A reset closes all sessions.' : '8 to 72 characters',
            })}
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
                  checked={roles.includes(role)}
                  onChange={(e) => toggleRole(role, e.target.checked)}
                />
                {ROLE_LABEL[role]}
              </label>
            ))}
            {fields.roles && <p className="text-sm text-destructive">{fields.roles}</p>}
          </CardContent>
        </Card>

        {isFarmer && (
          <Card>
            <CardHeader>
              <CardTitle>Farmer profile</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {text('memberNumber', 'Member number', memberNumber, setMemberNumber)}
              {text('cin', 'CIN', cin, setCin, { hint: '8 digits' })}
              <div className="flex flex-col gap-2">
                <Label htmlFor="delegation">Delegation</Label>
                <select
                  id="delegation"
                  value={delegation ?? ''}
                  onChange={(e) => setDelegation((e.target.value || null) as Delegation | null)}
                  aria-invalid={!!fields.delegation}
                  className="h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm"
                >
                  <option value="">Choose...</option>
                  {(Object.keys(DELEGATION_LABEL) as Delegation[]).map((d) => (
                    <option key={d} value={d}>
                      {DELEGATION_LABEL[d]}
                    </option>
                  ))}
                </select>
                {fields.delegation && <p className="text-sm text-destructive">{fields.delegation}</p>}
              </div>
              {text('address', 'Address (optional)', address, setAddress)}
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
