import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/mill/incoming')({ component: Page })

function Page() {
  return <RoleHome role="MILL_MANAGER" title="Incoming rounds" milestone="M7" />
}
