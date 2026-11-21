import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { ImportDetailPage } from '@/features/imports/ImportDetailPage'

export const Route = createFileRoute('/_app/intranet/imports/$id')({ component: Page })

function Page() {
  const { id } = Route.useParams()
  return (
    <RequireRole role="ADMIN">
      <ImportDetailPage id={id} />
    </RequireRole>
  )
}
