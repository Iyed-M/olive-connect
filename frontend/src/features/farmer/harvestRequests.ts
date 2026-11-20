import { api, ApiError } from '@/lib/api'
import type { Orchard } from './orchards'

export type HarvestType = 'PLANNED' | 'URGENT'
export type HarvestRequestStatus =
  | 'SUBMITTED'
  | 'VALIDATED'
  | 'REJECTED'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export type HarvestRequestAction = 'MODIFY' | 'CANCEL'

export interface HarvestRequestOrchard {
  id: string
  name: string
  delegation: string
  mainVarietyCode?: string | null
}

export interface StatusChange {
  from: HarvestRequestStatus | null
  to: HarvestRequestStatus
  at: string
  by?: string | null
  byUserId?: string | null
  comment?: string | null
}

export interface HarvestRequest {
  id: string
  orchard: HarvestRequestOrchard
  type: HarvestType
  desiredFrom: string
  desiredTo: string
  estimatedQuantityKg: number
  comment: string | null
  status: HarvestRequestStatus
  rejectionReason: string | null
  priorityScore: number
  roundId: string | null
  createdAt: string
  validatedAt: string | null
  history: StatusChange[]
  allowedActions: HarvestRequestAction[]
  version: number
}

export interface HarvestRequestWriteBody {
  orchardId: string
  type: HarvestType
  desiredFrom: string
  desiredTo: string
  estimatedQuantityKg: number | null
  comment: string | null
  version?: number
}

export interface HarvestRequestPage {
  content: HarvestRequest[]
  page: number
  size: number
  totalElements: number
}

export interface HarvestRequestFilters {
  mine?: boolean
  status?: HarvestRequestStatus
  type?: HarvestType
  page?: number
  size?: number
}

type HarvestRequestListResponse = HarvestRequest[] | HarvestRequestPage

function listParams({ mine, status, type, page = 0, size = 20 }: HarvestRequestFilters) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (mine !== undefined) params.set('mine', String(mine))
  if (status) params.set('status', status)
  if (type) params.set('type', type)
  return params
}

export function normalizeHarvestRequests(response: HarvestRequestListResponse): HarvestRequestPage {
  if (Array.isArray(response)) {
    return { content: response, page: 0, size: response.length, totalElements: response.length }
  }
  return response
}

export function listHarvestRequests(filters: HarvestRequestFilters = {}) {
  const params = listParams(filters)
  return api<HarvestRequestListResponse>(`/harvest-requests?${params}`).then(normalizeHarvestRequests)
}

export const getHarvestRequest = (id: string) => api<HarvestRequest>(`/harvest-requests/${id}`)

export const createHarvestRequest = (body: HarvestRequestWriteBody) =>
  api<HarvestRequest>('/harvest-requests', { method: 'POST', body: JSON.stringify(body) })

export const updateHarvestRequest = (id: string, body: HarvestRequestWriteBody) =>
  api<HarvestRequest>(`/harvest-requests/${id}`, { method: 'PUT', body: JSON.stringify(body) })

export const cancelHarvestRequest = (id: string, version: number) =>
  api<HarvestRequest>(`/harvest-requests/${id}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })

export const validateHarvestRequest = (id: string, version: number) =>
  api<HarvestRequest>(`/harvest-requests/${id}/validate`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })

export const rejectHarvestRequest = (id: string, version: number, reason: string) =>
  api<HarvestRequest>(`/harvest-requests/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ version, reason }),
  })

export function estimateForOrchard(orchard: Pick<Orchard, 'areaHa' | 'lastSeasonYieldKgHa'>): number {
  const yieldKgHa = orchard.lastSeasonYieldKgHa > 0 ? orchard.lastSeasonYieldKgHa : 1000
  return Math.max(0, Math.round(orchard.areaHa * yieldKgHa))
}

export function dateRangeError(from: string, to: string, today = localDate(new Date())): string | null {
  if (!from || !to) return 'Choose a start and an end date.'
  if (from < today) return 'Harvest dates cannot be in the past.'
  if (to <= from) return 'The end date must be after the start date.'
  return null
}

function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function requestErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'STALE_VERSION') {
      return 'This request changed since it was loaded. Reload it before trying again.'
    }
    return error.detail || 'The request could not be saved.'
  }
  return 'The request could not be saved. Check your connection and try again.'
}

export function sortRequestsByPriority(requests: HarvestRequest[]): HarvestRequest[] {
  return [...requests].sort((left, right) => {
    const priority = right.priorityScore - left.priorityScore
    if (priority !== 0) return priority
    return right.createdAt.localeCompare(left.createdAt)
  })
}

export function requestHistory(request: HarvestRequest): StatusChange[] {
  return request.history ?? []
}
