import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { RoundsGrid } from '@/features/intranet/RoundsGrid'

export const Route = createFileRoute('/_app/intranet/rounds')({ component: Page })

function Page() {
  return (
    <RequireRole role={['COLLECTION_MANAGER', 'ADMIN']}>
      <RoundsGrid />
    </RequireRole>
  )
}
