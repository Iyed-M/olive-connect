import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { getAccessToken } from '@/auth/session'

export type EventStreamStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'unavailable'
export type QueryKey = readonly unknown[]

const MAX_RECONNECT_DELAY_MS = 30_000

export function nextReconnectDelay(attempt: number): number {
  return Math.min(1000 * 2 ** Math.max(0, attempt), MAX_RECONNECT_DELAY_MS)
}

export function normalizeEventTicket(response: unknown): string {
  if (typeof response === 'string' && response) return response
  if (response && typeof response === 'object') {
    if ('ticket' in response && typeof response.ticket === 'string' && response.ticket) return response.ticket
    if ('value' in response && typeof response.value === 'string' && response.value) return response.value
  }
  throw new Error('Le ticket SSE est invalide.')
}

export function requestEventTicket(): Promise<string> {
  return api<unknown>('/events/ticket', { method: 'POST' }).then(normalizeEventTicket)
}

export function eventQueryKeys(eventType: string): QueryKey[] {
  const type = eventType.toLowerCase()
  if (type === 'alert') return [['alerts'], ['notifications']]
  if (type === 'notification') return [['notifications']]
  if (type.includes('alert')) return [['alerts'], ['notifications']]
  if (type.includes('notification')) return [['notifications']]
  if (type.includes('request') || type.includes('harvest')) return [['harvest-requests'], ['notifications']]
  if (type.includes('round')) return [['rounds'], ['notifications']]
  return [['alerts'], ['notifications']]
}

function eventType(event: MessageEvent): string {
  if (event.type !== 'message') return event.type
  if (typeof event.data !== 'string') return event.type
  try {
    const payload = JSON.parse(event.data) as unknown
    if (payload && typeof payload === 'object') {
      const fields = payload as Record<string, unknown>
      for (const key of ['type', 'event', 'kind']) {
        if (typeof fields[key] === 'string') return fields[key]
      }
    }
  } catch {
    // A plain message still invalidates the broad real-time query families.
  }
  return event.type
}

export function useEventStream(enabled = true): { status: EventStreamStatus; active: boolean } {
  const queryClient = useQueryClient()
  const accessToken = getAccessToken()
  const [status, setStatus] = useState<EventStreamStatus>(() => (
    !enabled ? 'unavailable' : !accessToken ? 'idle' : typeof EventSource === 'undefined' ? 'unavailable' : 'idle'
  ))

  useEffect(() => {
    if (!enabled || !accessToken) return
    if (typeof EventSource === 'undefined') return

    let disposed = false
    let source: EventSource | null = null
    let reconnectTimer: number | null = null
    let reconnectAttempt = 0
    let hasConnected = false

    const invalidateFor = (event: MessageEvent) => {
      for (const queryKey of eventQueryKeys(eventType(event))) {
        void queryClient.invalidateQueries({ queryKey })
      }
    }

    const scheduleReconnect = () => {
      if (disposed || reconnectTimer !== null) return
      source?.close()
      source = null
      setStatus('reconnecting')
      const delay = nextReconnectDelay(reconnectAttempt)
      reconnectAttempt += 1
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null
        void connect()
      }, delay)
    }

    const connect = async () => {
      if (disposed) return
      setStatus(reconnectAttempt === 0 ? 'connecting' : 'reconnecting')
      try {
        const ticket = await requestEventTicket()
        if (disposed) return
        const url = `/api/events?ticket=${encodeURIComponent(ticket)}`
        source = new EventSource(url)
        source.onopen = () => {
          if (disposed) return
          const reconnecting = hasConnected
          hasConnected = true
          reconnectAttempt = 0
          setStatus('connected')
          if (reconnecting) {
            void queryClient.invalidateQueries({ queryKey: ['notifications'] })
          }
        }
        source.onmessage = invalidateFor
        for (const name of ['alert', 'notification', 'request', 'round', 'NEW_ALERT', 'NEW_NOTIFICATION']) {
          source.addEventListener(name, invalidateFor)
        }
        source.onerror = scheduleReconnect
      } catch {
        scheduleReconnect()
      }
    }

    void connect()
    return () => {
      disposed = true
      if (reconnectTimer !== null) window.clearTimeout(reconnectTimer)
      source?.close()
      source = null
    }
  }, [accessToken, enabled, queryClient])

  const visibleStatus = !enabled ? 'unavailable' : !accessToken ? 'idle' : typeof EventSource === 'undefined' ? 'unavailable' : status
  return { status: visibleStatus, active: visibleStatus === 'connected' }
}
