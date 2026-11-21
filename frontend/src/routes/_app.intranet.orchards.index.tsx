import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { OrchardsGrid } from '@/features/intranet/OrchardsGrid'

export const Route = createFileRoute('/_app/intranet/orchards/')({ component: Page })

function Page() {
  return (
    <RequireRole role={['COLLECTION_MANAGER', 'LOGISTICS_MANAGER', 'ADMIN']}>
      <OrchardsGrid />
    </RequireRole>
  )
}
