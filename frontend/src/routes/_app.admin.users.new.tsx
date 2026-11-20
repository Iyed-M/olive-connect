import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { UserForm } from '@/admin/UserForm'

export const Route = createFileRoute('/_app/admin/users/new')({ component: Page })

function Page() {
  return (
    <RequireRole role="ADMIN">
      <h1 className="font-heading text-2xl font-bold text-primary">New user</h1>
      <UserForm />
    </RequireRole>
  )
}
