import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_TYPE_LABEL,
  unreadCount,
  type Notification,
} from './notifications'

const POLL_INTERVAL_MS = 30_000

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: () => listNotifications(),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
  const error = query.error instanceof ApiError ? query.error : null
  const notifications = [...(query.data ?? [])].sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  const unread = unreadCount(notifications)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Notifications</h1>
          <p className="text-sm text-muted-foreground">Updates for your harvest requests. This inbox checks for new items every 30 seconds.</p>
        </div>
        {unread > 0 && <Button variant="outline" onClick={() => readAll.mutate()} disabled={readAll.isPending}><CheckCheck aria-hidden /> Mark all read</Button>}
      </div>

      {readAll.isError && <p role="alert" className="text-sm text-destructive">Could not mark all notifications as read.</p>}
      {query.isPending && <p className="text-muted-foreground">Loading notifications...</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Could not load notifications.'}</p>}
      {query.data && notifications.length === 0 && <Card><CardContent className="flex flex-col items-center gap-2 py-8 text-center"><Bell className="size-8 text-muted-foreground" aria-hidden /><p>No notifications yet.</p></CardContent></Card>}
      {notifications.map((notification) => <NotificationItem key={notification.id} notification={notification} />)}
    </div>
  )
}

function NotificationItem({ notification }: { notification: Notification }) {
  const queryClient = useQueryClient()
  const read = useMutation({
    mutationFn: () => markNotificationRead(notification.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
  const typeLabel = NOTIFICATION_TYPE_LABEL[notification.type] ?? 'Notification'

  return (
    <Card className={notification.read ? 'opacity-75' : 'ring-2 ring-primary/20'}>
      <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 gap-3">
          <span className="mt-1 flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-primary"><Bell className="size-4" aria-hidden /></span>
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{typeLabel}</p>
            <h2 className="font-heading text-lg font-semibold">{notification.title}</h2>
            <p className="text-sm">{notification.message}</p>
            <time dateTime={notification.createdAt} className="text-xs text-muted-foreground">{formatDate(notification.createdAt)}</time>
            {notification.link && <a href={notification.link} className="mt-2 block text-sm text-primary underline">Open related page</a>}
          </div>
        </div>
        {!notification.read && <Button variant="outline" size="sm" onClick={() => read.mutate()} disabled={read.isPending}>{read.isPending ? 'Saving...' : 'Mark read'}</Button>}
        {read.isError && <p role="alert" className="text-sm text-destructive">Could not mark as read.</p>}
      </CardContent>
    </Card>
  )
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
