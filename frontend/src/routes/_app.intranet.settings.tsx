import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { SettingsPage } from '@/features/imports/SettingsPage'

export const Route = createFileRoute('/_app/intranet/settings')({ component: Page })

function Page() {
  return (
    <RequireRole role="ADMIN">
      <SettingsPage />
    </RequireRole>
  )
}
