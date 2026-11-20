import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/farmer')({ component: Page })

function Page() {
  return <RoleHome role="FARMER" title="Farmer home" milestone="M2" />
}
