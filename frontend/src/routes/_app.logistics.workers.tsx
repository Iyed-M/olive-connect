import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/logistics/workers')({ component: Page })

function Page() {
  return <RoleHome role="LOGISTICS_MANAGER" title="Workers" milestone="M4" />
}
