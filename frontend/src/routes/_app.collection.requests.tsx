import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/collection/requests')({ component: Page })

function Page() {
  return <RoleHome role="COLLECTION_MANAGER" title="Harvest requests" milestone="M3" />
}
