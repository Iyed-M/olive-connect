import { api } from '@/lib/api'
import type { GeoPoint, Orchard } from '@/features/farmer/orchards'

export type AlertType = 'MATURITY' | 'RAIN' | 'PEST' | 'DISEASE' | 'OTHER'
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH'
export type AlertSource = 'FARMER' | 'SENSOR'
export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED'
export type AlertAction = 'ACKNOWLEDGE' | 'RESOLVE' | 'DISMISS' | 'CREATE_URGENT_REQUEST'

export const MAX_ALERT_PHOTO_BYTES = 5 * 1024 * 1024
export const ALERT_PHOTO_TYPES = ['image/jpeg', 'image/png'] as const

export const ALERT_TYPE_LABEL: Record<AlertType, string> = {
  MATURITY: 'Maturité',
  RAIN: 'Pluie',
  PEST: 'Ravageurs',
  DISEASE: 'Maladie',
  OTHER: 'Autre',
}

export const ALERT_STATUS_LABEL: Record<AlertStatus, string> = {
  NEW: 'Nouvelle',
  ACKNOWLEDGED: 'Prise en charge',
  RESOLVED: 'Résolue',
  DISMISSED: 'Écartée',
}

export const ALERT_SEVERITY_LABEL: Record<AlertSeverity, string> = {
  LOW: 'Faible',
  MEDIUM: 'Moyenne',
  HIGH: 'Élevée',
}

export interface AlertStatusChange {
  from: AlertStatus | null
  to: AlertStatus
  at: string
  by?: string | null
  byUserId?: string | null
  comment?: string | null
}

export interface FieldAlert {
  id: string
  type: AlertType
  severity: AlertSeverity
  status: AlertStatus
  source: AlertSource
  orchardId: string
  orchardName: string
  farmerId?: string | null
  location: GeoPoint | null
  description: string | null
  photoUrls?: string[]
  resolution?: string | null
  statusHistory?: AlertStatusChange[]
  locationMismatch?: boolean
  createdAt: string
  allowedActions?: AlertAction[]
}

export interface AlertPage {
  content: FieldAlert[]
  page: number
  size: number
  totalElements: number
}

export interface AlertFilters {
  status?: AlertStatus
  type?: AlertType
  mine?: boolean
}

export interface AlertReportValues {
  orchardId: string
  type: AlertType
  severity: AlertSeverity
  description: string
  location: GeoPoint
  photo?: File | null
}

type AlertListResponse = FieldAlert[] | AlertPage

export function normalizeAlerts(response: AlertListResponse): FieldAlert[] {
  return Array.isArray(response) ? response : response.content
}

export function alertPhotoError(file: File | null | undefined): string | null {
  if (!file) return null
  if (!ALERT_PHOTO_TYPES.includes(file.type as (typeof ALERT_PHOTO_TYPES)[number])) {
    return 'La photo doit être au format JPEG ou PNG.'
  }
  if (file.size > MAX_ALERT_PHOTO_BYTES) {
    return 'La photo ne doit pas dépasser 5 Mo.'
  }
  return null
}

export function canCreateUrgentRequest(alert: Pick<FieldAlert, 'type' | 'status'>): boolean {
  return alert.type === 'MATURITY' && alert.status === 'ACKNOWLEDGED'
}

function alertQueryParams(filters: AlertFilters): string {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.type) params.set('type', filters.type)
  if (filters.mine !== undefined) params.set('mine', String(filters.mine))
  const query = params.toString()
  return query ? `?${query}` : ''
}

export function listAlerts(filters: AlertFilters = {}) {
  return api<AlertListResponse>(`/alerts${alertQueryParams(filters)}`).then(normalizeAlerts)
}

export function buildAlertFormData(values: AlertReportValues): FormData {
  const form = new FormData()
  const dto = {
    orchardId: values.orchardId,
    type: values.type,
    severity: values.severity,
    description: values.description.trim() || null,
    location: values.location,
  }
  form.append('alert', new Blob([JSON.stringify(dto)], { type: 'application/json' }))
  if (values.photo) form.append('photo', values.photo)
  return form
}

export function createAlert(values: AlertReportValues) {
  return api<FieldAlert>('/alerts', { method: 'POST', body: buildAlertFormData(values) })
}

export function normalizeNearestOrchard(response: unknown): Orchard | null {
  if (!response || typeof response !== 'object') return null
  const candidate = 'orchard' in response ? response.orchard : response
  if (!candidate || typeof candidate !== 'object' || !('id' in candidate) || !('name' in candidate)) return null
  return candidate as Orchard
}

/** Fallback used when GPS is available but the nearest-orchard endpoint is not. */
export function findNearestOrchardLocally(orchards: Orchard[], location: GeoPoint): Orchard | null {
  const [lng, lat] = location.coordinates
  return orchards.reduce<Orchard | null>((nearest, orchard) => {
    const [orchardLng, orchardLat] = orchard.location.coordinates
    const distance = (orchardLng - lng) ** 2 + (orchardLat - lat) ** 2
    if (!nearest) return orchard
    const [nearestLng, nearestLat] = nearest.location.coordinates
    const nearestDistance = (nearestLng - lng) ** 2 + (nearestLat - lat) ** 2
    return distance < nearestDistance ? orchard : nearest
  }, null)
}

export function getNearestOrchard(location: GeoPoint) {
  const [lng, lat] = location.coordinates
  return api<unknown>(`/orchards/nearest?lat=${encodeURIComponent(lat)}&lng=${encodeURIComponent(lng)}`)
    .then(normalizeNearestOrchard)
}

export const acknowledgeAlert = (id: string) =>
  api<FieldAlert>(`/alerts/${encodeURIComponent(id)}/acknowledge`, { method: 'POST' })

export const resolveAlert = (id: string, resolution?: string) =>
  api<FieldAlert>(`/alerts/${encodeURIComponent(id)}/resolve`, {
    method: 'POST',
    ...(resolution?.trim() ? { body: JSON.stringify({ resolution: resolution.trim() }) } : {}),
  })

export const dismissAlert = (id: string) =>
  api<FieldAlert>(`/alerts/${encodeURIComponent(id)}/dismiss`, { method: 'POST' })

export const createUrgentRequestFromAlert = (id: string) =>
  api<unknown>(`/alerts/${encodeURIComponent(id)}/create-urgent-request`, { method: 'POST' })

export function alertPhotoUrl(fileId: string): string {
  return fileId.startsWith('/') ? fileId : `/api/files/alerts/${encodeURIComponent(fileId)}`
}

export function alertErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return 'L’alerte n’a pas pu être envoyée. Vérifiez la connexion et réessayez.'
}
