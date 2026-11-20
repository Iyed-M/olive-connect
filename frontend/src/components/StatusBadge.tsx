import { Badge } from '@/components/ui/badge'
import type { HarvestRequestStatus } from '@/features/farmer/harvestRequests'

export const HARVEST_STATUS_LABEL: Record<HarvestRequestStatus, string> = {
  SUBMITTED: 'Submitted',
  VALIDATED: 'Validated',
  REJECTED: 'Rejected',
  SCHEDULED: 'Scheduled',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export function StatusBadge({ status }: { status: HarvestRequestStatus }) {
  const variant = status === 'REJECTED' || status === 'CANCELLED' ? 'destructive' : status === 'VALIDATED' || status === 'COMPLETED' ? 'default' : 'secondary'
  return <Badge variant={variant}>{HARVEST_STATUS_LABEL[status]}</Badge>
}
