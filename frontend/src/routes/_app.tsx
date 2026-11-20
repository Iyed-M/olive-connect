import { createFileRoute, redirect } from '@tanstack/react-router'
import { AppLayout } from '@/components/AppLayout'

// Pathless layout: it adds no segment to the URL (/_app/farmer is served as /farmer).
// Every page below it needs a logged-in User.
export const Route = createFileRoute('/_app')({
  beforeLoad: ({ context, location }) => {
    if (!context.auth.isAuthenticated()) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: AppLayout,
})
