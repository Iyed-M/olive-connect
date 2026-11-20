import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'

export const Route = createFileRoute('/admin/users')({
  // Minimal guard: logged-out visitors go to /login. Role checks come with the layout ticket.
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated()) throw redirect({ to: '/login' })
  },
  component: UsersPage,
})

function UsersPage() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    await navigate({ to: '/login' })
  }

  return (
    <>
      <h1 className="text-2xl font-bold text-primary">Users</h1>
      <p className="text-muted-foreground">Users - coming in M1b.</p>
      {/* TEMPORARY: the real header with the logout button comes with the layout ticket. */}
      <Button variant="outline" onClick={onLogout}>
        Log out
      </Button>
    </>
  )
}
