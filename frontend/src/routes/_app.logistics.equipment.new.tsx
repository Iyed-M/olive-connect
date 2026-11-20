import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { EquipmentForm } from '@/logistics/EquipmentForm'

export const Route = createFileRoute('/_app/logistics/equipment/new')({ component: Page })

function Page() {
  return (
    <RequireRole role="LOGISTICS_MANAGER">
      <h1 className="font-heading text-2xl font-bold text-primary">New equipment</h1>
      <EquipmentForm />
    </RequireRole>
  )
}
