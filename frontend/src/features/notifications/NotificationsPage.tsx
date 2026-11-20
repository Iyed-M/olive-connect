import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_POLL_INTERVAL_MS,
  NOTIFICATION_TYPE_LABEL,
  notificationPath,
  unreadCount,
  type Notification,
} from './notifications'

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: () => listNotifications(),
    refetchInterval: NOTIFICATION_POLL_INTERVAL_MS,
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
          <p className="text-sm text-muted-foreground">Les mises à jour de vos demandes de récolte. Cette boîte vérifie les nouveaux messages toutes les 30 secondes.</p>
        </div>
        {unread > 0 && <Button variant="outline" onClick={() => readAll.mutate()} disabled={readAll.isPending}><CheckCheck aria-hidden /> Tout marquer comme lu</Button>}
      </div>

      {readAll.isError && <p role="alert" className="text-sm text-destructive">Impossible de marquer toutes les notifications comme lues.</p>}
      {query.isPending && <p className="text-muted-foreground">Chargement des notifications…</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Impossible de charger les notifications.'}</p>}
      {query.data && notifications.length === 0 && <Card><CardContent className="flex flex-col items-center gap-2 py-8 text-center"><Bell className="size-8 text-muted-foreground" aria-hidden /><p>Aucune notification pour le moment.</p></CardContent></Card>}
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
  const relatedPath = notificationPath(notification)

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
            {relatedPath && <a href={relatedPath} className="mt-2 inline-flex min-h-11 items-center text-sm text-primary underline">Ouvrir la page associée</a>}
          </div>
        </div>
        {!notification.read && <Button variant="outline" onClick={() => read.mutate()} disabled={read.isPending}>{read.isPending ? 'Enregistrement…' : 'Marquer comme lue'}</Button>}
        {read.isError && <p role="alert" className="text-sm text-destructive">Impossible de marquer la notification comme lue.</p>}
      </CardContent>
    </Card>
  )
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
