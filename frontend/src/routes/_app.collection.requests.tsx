import { createFileRoute } from '@tanstack/react-router'
import { RequestQueuePage } from '@/features/collection/RequestQueue'

export const Route = createFileRoute('/_app/collection/requests')({ component: Page })

function Page() {
  return <RequestQueuePage />
}
