import { createFileRoute } from '@tanstack/react-router'
import { RoleHome } from '@/components/RoleHome'

export const Route = createFileRoute('/_app/admin/users')({ component: Page })

function Page() {
  return <RoleHome role="ADMIN" title="Users" milestone="M1b" />
}
