import { createFileRoute } from '@tanstack/react-router'
import { MyRoundsPage } from '@/features/teamleader/MyRounds'

export const Route = createFileRoute('/_app/rounds/mine')({ component: Page })

function Page() {
  return <MyRoundsPage />
}
