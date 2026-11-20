import { describe, expect, it } from 'vitest'
import { notificationPath, unreadCount, normalizeNotifications, type Notification } from './notifications'

const notification = (id: string, read: boolean): Notification => ({
  id,
  type: 'NEW_REQUEST',
  title: 'New request',
  message: 'A farmer sent a request.',
  link: '/collection/requests',
  read,
  createdAt: '2026-11-20T09:00:00Z',
})

describe('normalizeNotifications', () => {
  it('supports both the plain list and paged API response', () => {
    const items = [notification('1', false)]
    expect(normalizeNotifications(items)).toEqual(items)
    expect(normalizeNotifications({ content: items, page: 0, size: 20, totalElements: 1 })).toEqual(items)
  })
})

describe('unreadCount', () => {
  it('counts only unread notifications', () => {
    expect(unreadCount([notification('1', false), notification('2', true), notification('3', false)])).toBe(2)
  })
})

describe('notificationPath', () => {
  it('maps API request links to the appropriate SPA page', () => {
    expect(notificationPath({ type: 'NEW_REQUEST', link: '/harvest-requests/abc' })).toBe('/collection/requests')
    expect(notificationPath({ type: 'REQUEST_VALIDATED', link: '/harvest-requests/abc' })).toBe('/farmer/requests/abc')
    expect(notificationPath({ type: 'REQUEST_REJECTED', link: null })).toBeNull()
  })

  it('opens an assigned round in the team leader runner', () => {
    expect(notificationPath({ type: 'ROUND_ASSIGNED', link: '/rounds/round-1' })).toBe('/rounds/round-1/run')
  })
})
