import { describe, expect, it } from 'vitest'
import { eventQueryKeys, nextReconnectDelay, normalizeEventTicket } from './eventStream'

describe('event stream helpers', () => {
  it('routes alert and notification events to their query families', () => {
    expect(eventQueryKeys('alert')).toEqual([['alerts'], ['notifications']])
    expect(eventQueryKeys('notification')).toEqual([['notifications']])
    expect(eventQueryKeys('NEW_ALERT')).toEqual([['alerts'], ['notifications']])
    expect(eventQueryKeys('unknown')).toEqual([['alerts'], ['notifications']])
  })

  it('backs off reconnects but caps the delay', () => {
    expect(nextReconnectDelay(0)).toBe(1000)
    expect(nextReconnectDelay(1)).toBe(2000)
    expect(nextReconnectDelay(5)).toBe(30_000)
    expect(nextReconnectDelay(20)).toBe(30_000)
  })

  it('normalizes the ticket response from the endpoint', () => {
    expect(normalizeEventTicket('ticket-1')).toBe('ticket-1')
    expect(normalizeEventTicket({ ticket: 'ticket-2' })).toBe('ticket-2')
    expect(normalizeEventTicket({ value: 'ticket-3' })).toBe('ticket-3')
  })
})
