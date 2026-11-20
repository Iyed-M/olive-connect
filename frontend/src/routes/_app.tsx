import { createFileRoute, redirect } from '@tanstack/react-router'
import { getUser } from '@/auth/session'
import { AppLayout } from '@/components/AppLayout'

// Pathless layout: it adds no segment to the URL (/_app/farmer is served as /farmer).
// Every page below it needs a logged-in User.
export const Route = createFileRoute('/_app')({
  beforeLoad: ({ context, location }) => {
    if (!context.auth.isAuthenticated()) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
    // A temporary password must be changed before anything else.
    if (getUser()?.mustChangePassword && location.pathname !== '/change-password') {
      throw redirect({ to: '/change-password' })
    }
  },
  component: AppLayout,
})
