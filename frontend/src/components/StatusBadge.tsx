import { Badge } from '@/components/ui/badge'
import type { HarvestRequestStatus } from '@/features/farmer/harvestRequests'

export const HARVEST_STATUS_LABEL: Record<HarvestRequestStatus, string> = {
  SUBMITTED: 'Soumise',
  VALIDATED: 'Validée',
  REJECTED: 'Refusée',
  SCHEDULED: 'Planifiée',
  IN_PROGRESS: 'En cours',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
}

export function StatusBadge({ status }: { status: HarvestRequestStatus }) {
  const variant = status === 'REJECTED' || status === 'CANCELLED' ? 'destructive' : status === 'VALIDATED' || status === 'COMPLETED' ? 'default' : 'secondary'
  return <Badge variant={variant}>{HARVEST_STATUS_LABEL[status]}</Badge>
}
