import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { AlertForm } from '@/features/alerts/AlertForm'

export const Route = createFileRoute('/_app/farmer/alerts/new')({ component: Page })

function Page() {
  return <RequireRole role="FARMER"><AlertForm /></RequireRole>
}
