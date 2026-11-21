import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { ResourceCalendar } from '@/logistics/ResourceCalendar'

export const Route = createFileRoute('/_app/logistics/calendar')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'COLLECTION_MANAGER', 'ADMIN']}>
      <ResourceCalendar />
    </RequireRole>
  )
}
