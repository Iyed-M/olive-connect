import { createFileRoute } from '@tanstack/react-router'
import { RoundRunnerPage } from '@/features/teamleader/RoundRunner'

export const Route = createFileRoute('/_app/rounds/$id/run')({ component: Page })

function Page() {
  const { id } = Route.useParams()
  return <RoundRunnerPage id={id} />
}
