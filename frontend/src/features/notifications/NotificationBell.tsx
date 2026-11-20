import { Bell } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { listNotifications, unreadCount } from './notifications'

const POLL_INTERVAL_MS = 30_000

export function NotificationBell() {
  const query = useQuery({
    queryKey: ['notifications', { unread: true }],
    queryFn: () => listNotifications(true),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })
  const count = query.data ? unreadCount(query.data) : 0

  return (
    <Link
      to="/notifications"
      aria-label={count > 0 ? `${count} unread notifications` : 'Notifications'}
      className="relative inline-flex h-11 min-w-11 items-center justify-center rounded-lg border border-transparent text-primary hover:bg-muted focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <Bell aria-hidden className="size-5" />
      {count > 0 && <span aria-hidden className="absolute right-1 top-1 flex min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[0.65rem] font-bold text-destructive-foreground">{count > 99 ? '99+' : count}</span>}
    </Link>
  )
}
