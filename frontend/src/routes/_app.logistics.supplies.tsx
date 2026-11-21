import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { SupplyOrders } from '@/logistics/SupplyOrders'

export const Route = createFileRoute('/_app/logistics/supplies')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'ADMIN']}>
      <SupplyOrders />
    </RequireRole>
  )
}
