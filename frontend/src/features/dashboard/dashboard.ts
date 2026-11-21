import { refresh } from '@/auth/refresh'
import { getAccessToken } from '@/auth/session'
import { api, ApiError } from '@/lib/api'

export const DASHBOARD_ROLES = ['COLLECTION_MANAGER', 'MILL_MANAGER', 'ADMIN'] as const

export type DashboardPeriod = 'season' | 'last-7-days' | 'last-30-days'

export interface DashboardFilters {
  season: string
  period: DashboardPeriod
  variety: string
  delegation: string
}

export const DEFAULT_DASHBOARD_FILTERS: DashboardFilters = {
  season: '2026-2027',
  period: 'season',
  variety: '',
  delegation: '',
}

export interface DashboardSummary {
  seasonTonnageKg: number
  avgYieldKgHa: number
  avgLeadTimeHours: number
  avgFillRate: number
}

export interface YieldPoint {
  label: string
  orchardId?: string
  orchardName?: string
  variety?: string
  delegation?: string
  yieldKgHa: number
}

export interface LeadTimePoint {
  label: string
  avgLeadTimeHours: number
  p90LeadTimeHours: number
}

export interface FillRatePoint {
  label: string
  avgFillRate: number
  receptions: number
}

export interface MillVolumePoint {
  date: string
  millName: string
  volumeKg: number
}

export type DashboardState = 'loading' | 'error' | 'empty' | 'ready'

interface DashboardQueryOptions {
  groupBy?: string
  now?: Date
}

type JsonRecord = Record<string, unknown>

export function buildDashboardQuery(filters: DashboardFilters, options: DashboardQueryOptions = {}): string {
  const params = new URLSearchParams()
  const season = filters.season.trim()
  const variety = filters.variety.trim()
  const delegation = filters.delegation.trim()

  if (season) params.set('season', season)
  if (variety) params.set('variety', variety)
  if (delegation) params.set('delegation', delegation)

  if (filters.period !== 'season') {
    const to = options.now ?? new Date()
    const days = filters.period === 'last-7-days' ? 7 : 30
    const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
    params.set('from', from.toISOString())
    params.set('to', to.toISOString())
  }

  if (options.groupBy) params.set('groupBy', options.groupBy)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export function dashboardState({
  loading,
  error,
  hasData,
}: {
  loading: boolean
  error: unknown | null
  hasData: boolean
}): DashboardState {
  if (loading) return 'loading'
  if (error) return 'error'
  return hasData ? 'ready' : 'empty'
}

export function normalizeDashboardSummary(raw: unknown): DashboardSummary {
  const value = asRecord(raw)
  return {
    seasonTonnageKg: numberValue(value, ['seasonTonnageKg', 'totalNetKg', 'totalNetWeightKg', 'tonnageKg']),
    avgYieldKgHa: numberValue(value, ['avgYieldKgHa', 'averageYieldKgHa', 'yieldKgHa']),
    avgLeadTimeHours: numberValue(value, ['avgLeadTimeHours', 'avgLeadTimeH', 'averageLeadTimeHours']),
    avgFillRate: numberValue(value, ['avgFillRate', 'averageFillRate', 'fillRate']),
  }
}

export function normalizeYieldPoints(raw: unknown): YieldPoint[] {
  return responseItems(raw).map((item) => {
    const value = asRecord(item)
    const orchardId = stringValue(value, ['orchardId'])
    const orchardName = stringValue(value, ['orchardName', 'name'])
    const variety = stringValue(value, ['variety', 'varietyCode'])
    const delegation = stringValue(value, ['delegation', 'delegationCode'])
    const label = orchardName || delegation || variety || orchardId || dimensionLabel(value) || '—'
    const point: YieldPoint = {
      label,
      yieldKgHa: numberValue(value, ['yieldKgHa', 'averageYieldKgHa']),
    }
    if (orchardId) point.orchardId = orchardId
    if (orchardName) point.orchardName = orchardName
    if (variety) point.variety = variety
    if (delegation) point.delegation = delegation
    return point
  })
}

export function normalizeLeadTimePoints(raw: unknown): LeadTimePoint[] {
  return responseItems(raw).map((item) => {
    const value = asRecord(item)
    return {
      label: dimensionLabel(value) || '—',
      avgLeadTimeHours: numberValue(value, ['avgLeadTimeHours', 'avgLeadTimeH', 'averageLeadTimeHours']),
      p90LeadTimeHours: numberValue(value, ['p90LeadTimeHours', 'p90LeadTimeH', 'p90Hours']),
    }
  })
}

export function normalizeFillRatePoints(raw: unknown): FillRatePoint[] {
  return responseItems(raw).map((item) => {
    const value = asRecord(item)
    const week = numberValue(value, ['week'])
    const year = numberValue(value, ['year', 'isoWeekYear'])
    const label = week > 0 && year > 0 ? `${year}-W${String(week).padStart(2, '0')}` : dimensionLabel(value) || '—'
    return {
      label,
      avgFillRate: numberValue(value, ['avgFillRate', 'averageFillRate', 'fillRate']),
      receptions: numberValue(value, ['receptions', 'receptionCount', 'count']),
    }
  })
}

export function normalizeMillVolumePoints(raw: unknown): MillVolumePoint[] {
  return responseItems(raw).map((item) => {
    const value = asRecord(item)
    return {
      date: stringValue(value, ['date', 'day', 'receivedDate']) || dimensionLabel(value) || '—',
      millName: stringValue(value, ['millName', 'mill', 'name', 'millId']) || 'Mill',
      volumeKg: numberValue(value, ['volumeKg', 'totalNetKg', 'netWeightKg', 'millVolumeKg']),
    }
  })
}

export function dashboardHasData(
  summary: DashboardSummary | undefined,
  yieldPoints: YieldPoint[],
  leadTimePoints: LeadTimePoint[],
  fillRatePoints: FillRatePoint[],
  millVolumePoints: MillVolumePoint[],
): boolean {
  return Boolean(
    yieldPoints.length ||
      leadTimePoints.length ||
      fillRatePoints.length ||
      millVolumePoints.length ||
      summary &&
        (summary.seasonTonnageKg > 0 ||
          summary.avgYieldKgHa > 0 ||
          summary.avgLeadTimeHours > 0 ||
          summary.avgFillRate > 0),
  )
}

export const getDashboardSummary = (filters: DashboardFilters) =>
  api<unknown>(`/dashboard/summary${buildDashboardQuery(filters)}`).then(normalizeDashboardSummary)

export type YieldGroupBy = 'orchard' | 'variety' | 'delegation'

export const getDashboardYield = (filters: DashboardFilters, groupBy: YieldGroupBy = 'delegation') =>
  api<unknown>(`/dashboard/yield${buildDashboardQuery(filters, { groupBy })}`).then(normalizeYieldPoints)

export const getDashboardLeadTime = (filters: DashboardFilters) =>
  api<unknown>(`/dashboard/lead-time${buildDashboardQuery(filters, { groupBy: 'delegation' })}`).then(normalizeLeadTimePoints)

export const getDashboardFillRate = (filters: DashboardFilters) =>
  api<unknown>(`/dashboard/fill-rate${buildDashboardQuery(filters, { groupBy: 'week' })}`).then(normalizeFillRatePoints)

export const getDashboardMillVolumes = (filters: DashboardFilters, millId?: string | null) => {
  const query = buildMillVolumesQuery(filters)
  const separator = query ? '&' : '?'
  const mill = millId?.trim() ? `${separator}millId=${encodeURIComponent(millId.trim())}` : ''
  return api<unknown>(`/dashboard/mill-volumes${query}${mill}`).then(normalizeMillVolumePoints)
}

export type DashboardExportKpi = 'summary' | 'yield' | 'lead-time' | 'fill-rate' | 'mill-volumes'

export async function downloadDashboardCsv(
  kpi: DashboardExportKpi,
  filters: DashboardFilters,
  millId?: string | null,
): Promise<void> {
  const query = buildDashboardQuery(filters, { groupBy: kpi === 'yield' ? 'orchard' : undefined })
  const exportQuery = kpi === 'mill-volumes' ? buildMillVolumesQuery(filters) : query
  const separator = exportQuery ? '&' : '?'
  const mill = millId?.trim() ? `${separator}millId=${encodeURIComponent(millId.trim())}` : ''
  const path = `/dashboard/${kpi}/export.csv${exportQuery}${mill}`
  let response = await fetch(`/api${path}`, {
    credentials: 'same-origin',
    headers: authorizationHeaders(),
  })

  if (response.status === 401 && getAccessToken() && (await refresh())) {
    response = await fetch(`/api${path}`, {
      credentials: 'same-origin',
      headers: authorizationHeaders(),
    })
  }

  if (!response.ok) throw await apiErrorFromResponse(response)

  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${kpi}-dashboard.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

function authorizationHeaders(): HeadersInit {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function buildMillVolumesQuery(filters: DashboardFilters): string {
  if (filters.period !== 'season') return buildDashboardQuery(filters)
  const match = /^(\d{4})-(\d{4})$/.exec(filters.season.trim())
  if (!match) return buildDashboardQuery(filters)

  const params = new URLSearchParams(buildDashboardQuery(filters).slice(1))
  params.set('from', `${match[1]}-07-01T00:00:00.000Z`)
  params.set('to', `${match[2]}-07-01T00:00:00.000Z`)
  return `?${params.toString()}`
}

async function apiErrorFromResponse(response: Response): Promise<ApiError> {
  const problem = await response.json().catch(() => null) as JsonRecord | null
  return new ApiError(
    response.status,
    stringValue(problem ?? {}, ['code']) || 'UNKNOWN',
    stringValue(problem ?? {}, ['detail']) || response.statusText,
    Array.isArray(problem?.errors) ? problem.errors as { field: string; message: string }[] : [],
    typeof problem?.retryAfterSeconds === 'number' ? problem.retryAfterSeconds : undefined,
  )
}

function asRecord(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {}
}

function responseItems(value: unknown): unknown[] {
  if (Array.isArray(value)) return value
  const record = asRecord(value)
  for (const key of ['content', 'points', 'data', 'values', 'series', 'items', 'results']) {
    if (Array.isArray(record[key])) return record[key]
  }
  return []
}

function numberValue(value: JsonRecord, keys: string[]): number {
  for (const key of keys) {
    const candidate = value[key]
    if (typeof candidate === 'number' && Number.isFinite(candidate)) return candidate
    if (typeof candidate === 'string' && candidate.trim() !== '') {
      const parsed = Number(candidate)
      if (Number.isFinite(parsed)) return parsed
    }
  }
  return 0
}

function stringValue(value: JsonRecord, keys: string[]): string {
  for (const key of keys) {
    const candidate = value[key]
    if (typeof candidate === 'string' && candidate.trim()) return candidate
    if (typeof candidate === 'number' && Number.isFinite(candidate)) return String(candidate)
  }
  return ''
}

function dimensionLabel(value: JsonRecord): string {
  const direct = stringValue(value, ['label', 'key', 'delegationLabel', 'delegation', 'period', 'weekLabel'])
  if (direct) return direct

  const id = asRecord(value._id)
  const idLabel = stringValue(id, ['label', 'delegation', 'key', 'date'])
  if (idLabel) return idLabel

  const week = numberValue(id, ['week'])
  const year = numberValue(id, ['year', 'isoWeekYear'])
  return week > 0 && year > 0 ? `${year}-W${String(week).padStart(2, '0')}` : stringValue(value, ['_id'])
}
