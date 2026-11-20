import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { HarvestRequestForm } from '@/features/farmer/HarvestRequestForm'

export const Route = createFileRoute('/_app/farmer/requests/new')({ component: Page })

function Page() {
  return <RequireRole role="FARMER"><HarvestRequestForm /></RequireRole>
}
