import { api } from '@/lib/api'
import type { PageResponse } from '@/admin/users'

export type ImportStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
export type ImportRunType = 'REFERENCE' | 'DYNAMIC'
export const IMPORT_PAGE_SIZE = 20

export interface ImportError {
  line: number
  field: string
  reason: string
}

// Mirrors the backend ImportJob (docs/04). `errors` holds at most 500 rows; `errorCount` is the total.
export interface ImportJob {
  id: string
  source: string
  dataset: string
  fileName: string
  status: ImportStatus
  totalRows: number
  insertedRows: number
  updatedRows: number
  rejectedRows: number
  startedAt: string
  finishedAt: string | null
  errors?: ImportError[]
  errorCount?: number
}

export interface ImportFilters {
  dataset: string
  status: ImportStatus | ''
  page: number
}

export const IMPORT_STATUS_LABEL: Record<ImportStatus, string> = {
  RUNNING: 'En cours',
  SUCCESS: 'Réussi',
  PARTIAL: 'Partiel',
  FAILED: 'Échoué',
}

export function listImports({ dataset, status, page }: ImportFilters) {
  const params = new URLSearchParams({ page: String(page), size: String(IMPORT_PAGE_SIZE) })
  if (dataset.trim()) params.set('dataset', dataset.trim())
  if (status) params.set('status', status)
  return api<PageResponse<ImportJob>>(`/imports?${params}`)
}

export const getImport = (id: string) => api<ImportJob>(`/imports/${id}`)

export const runImport = (type: ImportRunType) => api<void>(`/imports/run?type=${type}`, { method: 'POST' })

export function uploadImport(file: File) {
  const body = new FormData()
  body.append('file', file)
  return api<void>('/imports/upload', { method: 'POST', body })
}

// A CSV cell is quoted when it holds a separator, a quote or a line break; quotes are doubled.
function csvCell(value: string | number): string {
  const text = String(value)
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function errorsToCsv(errors: ImportError[]): string {
  const rows = errors.map((e) => [e.line, e.field, e.reason].map(csvCell).join(','))
  return ['line,field,reason', ...rows].join('\n')
}

export function downloadErrorsCsv(job: ImportJob) {
  const blob = new Blob([errorsToCsv(job.errors ?? [])], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${job.fileName}-rejected.csv`
  link.click()
  URL.revokeObjectURL(url)
}

// --- settings (docs/05 system_settings, `_id: "global"`) ---

export interface SystemSettings {
  rainAlertMm24h: number
  [key: string]: unknown // other parameters are kept as they are when saving
}

export const getSettings = () => api<SystemSettings>('/settings')

export const updateSettings = (body: SystemSettings) =>
  api<SystemSettings>('/settings', { method: 'PUT', body: JSON.stringify(body) })

// Form text to number; null when empty or not a number.
export function parseNumber(text: string): number | null {
  const value = text.trim().replace(',', '.')
  if (value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}
