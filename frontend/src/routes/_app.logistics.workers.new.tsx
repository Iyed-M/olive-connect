import { createFileRoute } from '@tanstack/react-router'
import { RequireRole } from '@/components/RequireRole'
import { WorkerForm } from '@/logistics/WorkerForm'

export const Route = createFileRoute('/_app/logistics/workers/new')({ component: Page })

function Page() {
  return (
    <RequireRole role="LOGISTICS_MANAGER">
      <h1 className="font-heading text-2xl font-bold text-primary">New worker</h1>
      <WorkerForm />
    </RequireRole>
  )
}
