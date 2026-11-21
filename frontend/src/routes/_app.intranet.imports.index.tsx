import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { ImportJournalPage } from '@/features/imports/ImportJournalPage'

export const Route = createFileRoute('/_app/intranet/imports/')({ component: Page })

function Page() {
  return (
    <RequireRole role="ADMIN">
      <ImportJournalPage />
    </RequireRole>
  )
}
