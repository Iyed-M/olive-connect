import { createFileRoute } from '@tanstack/react-router'
import { HarvestRequestDetailPage } from '@/features/farmer/HarvestRequestDetail'

export const Route = createFileRoute('/_app/farmer/requests/$id')({ component: Page })

function Page() {
  const { id } = Route.useParams()
  return <HarvestRequestDetailPage id={id} />
}
