import { createFileRoute } from '@tanstack/react-router'
import { OrchardForm } from '@/features/farmer/OrchardForm'
import { RequireRole } from '@/components/RequireRole'

export const Route = createFileRoute('/_app/farmer/orchards/new')({ component: Page })

function Page() {
  return <RequireRole role="FARMER"><OrchardForm /></RequireRole>
}
