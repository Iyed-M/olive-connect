import { createFileRoute } from '@tanstack/react-router'
import { IncomingRoundsPage } from '@/features/mill/IncomingRoundsPage'

export const Route = createFileRoute('/_app/mill/incoming')({ component: Page })

function Page() {
  return <IncomingRoundsPage />
}
