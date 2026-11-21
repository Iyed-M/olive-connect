import { Badge } from '@/components/ui/badge'
import { IMPORT_STATUS_LABEL, type ImportStatus } from './imports'

export function ImportStatusBadge({ status }: { status: ImportStatus }) {
  const variant = status === 'FAILED' ? 'destructive' : status === 'SUCCESS' ? 'default' : 'secondary'
  return <Badge variant={variant}>{IMPORT_STATUS_LABEL[status]}</Badge>
}
