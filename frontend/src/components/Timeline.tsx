import type { StatusChange } from '@/features/farmer/harvestRequests'
import { HARVEST_STATUS_LABEL } from './StatusBadge'

export function Timeline({ history }: { history: StatusChange[] }) {
  if (history.length === 0) return <p className="text-sm text-muted-foreground">No status history yet.</p>

  return (
    <ol aria-label="Status timeline" className="flex flex-col gap-3 border-l-2 border-secondary pl-4">
      {history.map((change, index) => (
        <li key={`${change.at}-${change.to}-${index}`} className="relative flex flex-col gap-0.5">
          <span className="absolute -left-[1.4rem] top-1 size-2 rounded-full bg-primary" aria-hidden />
          <span className="font-medium">{HARVEST_STATUS_LABEL[change.to]}</span>
          <time dateTime={change.at} className="text-xs text-muted-foreground">{formatTimelineDate(change.at)}</time>
          {change.comment && <span className="text-sm text-muted-foreground">{change.comment}</span>}
        </li>
      ))}
    </ol>
  )
}

function formatTimelineDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
