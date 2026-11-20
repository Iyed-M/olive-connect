import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/admin/users')({
  // Minimal guard: logged-out visitors go to /login. Role checks come with the layout ticket.
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated()) throw redirect({ to: '/login' })
  },
  component: () => (
    <>
      <h1 className="text-2xl font-bold text-primary">Users</h1>
      <p className="text-muted-foreground">Users - coming in M1b.</p>
    </>
  ),
})
