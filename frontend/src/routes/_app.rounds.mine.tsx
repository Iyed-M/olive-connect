import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/rounds/mine')({ component: Page })

function Page() {
  return <RoleHome role="TEAM_LEADER" title="My rounds" milestone="M5" />
}
