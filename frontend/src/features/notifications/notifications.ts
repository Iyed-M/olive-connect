import { api } from '@/lib/api'

export type NotificationType = 'NEW_REQUEST' | 'REQUEST_VALIDATED' | 'REQUEST_REJECTED'

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

export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  NEW_REQUEST: 'New harvest request',
  REQUEST_VALIDATED: 'Request validated',
  REQUEST_REJECTED: 'Request rejected',
}
