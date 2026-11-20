import { api } from '@/lib/api'

export type NotificationType =
  | 'NEW_REQUEST'
  | 'REQUEST_VALIDATED'
  | 'REQUEST_REJECTED'
  | 'ROUND_SCHEDULED'
  | 'ROUND_ASSIGNED'
  | 'ROUND_UPDATED'
  | 'ROUND_CANCELLED'
  | 'ROUND_STARTED'
  | 'ROUND_COMPLETED'
export const NOTIFICATION_POLL_INTERVAL_MS = 30_000

export interface Notification {
  id: string
  type: NotificationType
  title: string
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

interface NotificationPage {
  content: Notification[]
  page: number
  size: number
  totalElements: number
}

type NotificationListResponse = Notification[] | NotificationPage

export function normalizeNotifications(response: NotificationListResponse): Notification[] {
  return Array.isArray(response) ? response : response.content
}

export function listNotifications(unread?: boolean) {
  const query = unread === undefined ? '' : `?unread=${String(unread)}`
  return api<NotificationListResponse>(`/notifications${query}`).then(normalizeNotifications)
}

export const markNotificationRead = (id: string) =>
  api<Notification>(`/notifications/${id}/read`, { method: 'POST' })

export const markAllNotificationsRead = () =>
  api<void>('/notifications/read-all', { method: 'POST' })

export function unreadCount(notifications: Notification[]): number {
  return notifications.filter((notification) => !notification.read).length
}

/** Convert the API resource link into the role-aware page that can open it in the SPA. */
export function notificationPath(notification: Pick<Notification, 'type' | 'link'>): string | null {
  if (!notification.link) return null
  const roundId = /^\/rounds\/([^/]+)$/.exec(notification.link)?.[1]
  if (roundId && notification.type === 'ROUND_ASSIGNED') return `/rounds/${roundId}/run`
  const requestId = /^\/harvest-requests\/([^/]+)$/.exec(notification.link)?.[1]
  if (!requestId) return notification.link
  return notification.type === 'NEW_REQUEST' ? '/collection/requests' : `/farmer/requests/${requestId}`
}

export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  NEW_REQUEST: 'Nouvelle demande de récolte',
  REQUEST_VALIDATED: 'Demande validée',
  REQUEST_REJECTED: 'Demande refusée',
  ROUND_SCHEDULED: 'Tournée planifiée',
  ROUND_ASSIGNED: 'Tournée attribuée',
  ROUND_UPDATED: 'Tournée mise à jour',
  ROUND_CANCELLED: 'Tournée annulée',
  ROUND_STARTED: 'Tournée démarrée',
  ROUND_COMPLETED: 'Tournée terminée',
}
