import { api } from '@/lib/api'
import { PAGE_SIZE, type PageResponse } from '@/admin/users'

// All API types of the Workers and Equipment pages live in this file. They mirror the backend
// DTOs of M4 (docs/04 classes Worker, Equipment, Tractor, Bin, Tool, Unavailability).
// If the backend field names change, adjust them here only.

// Same values as the Java EquipmentType, EquipmentStatus and ToolType enums.
export const EQUIPMENT_TYPE_LABEL = {
  TRACTOR: 'Tractor',
  BIN: 'Bin',
  NET: 'Net',
  SHAKER: 'Shaker',
} as const
export type EquipmentType = keyof typeof EQUIPMENT_TYPE_LABEL

export const EQUIPMENT_STATUS_LABEL = {
  AVAILABLE: 'Available',
  MAINTENANCE: 'Maintenance',
  OUT_OF_ORDER: 'Out of order',
} as const
export type EquipmentStatus = keyof typeof EQUIPMENT_STATUS_LABEL

// A Tool is a NET or a SHAKER: its toolType equals its type.
export type ToolType = 'NET' | 'SHAKER'

// Instants are ISO-8601 strings.
export interface Unavailability {
  from: string
  to: string
  reason: string | null
}

export interface WorkerDto {
  id: string
  firstName: string
  lastName: string
  cin: string
  phone: string | null
  skills: string[]
  dailyRate: number
  active: boolean
  unavailabilities: Unavailability[]
  version: number
}

export interface WorkerWriteBody {
  firstName: string
  lastName: string
  cin: string
  phone: string | null
  skills: string[]
  dailyRate: number | null
  active: boolean
}

// One flat DTO for the three kinds: `type` tells which optional fields are filled.
export interface EquipmentDto {
  id: string
  code: string
  type: EquipmentType
  status: EquipmentStatus
  plateNumber: string | null // TRACTOR
  powerHp: number | null // TRACTOR
  serialNumber: string | null // BIN
  capacityKg: number | null // BIN
  toolType: ToolType | null // NET, SHAKER
  stockQuantity: number | null // NET, SHAKER
  unavailabilities: Unavailability[]
  version: number
}

// The body of POST and PUT: the fields of the other kinds are sent as null.
export interface EquipmentWriteBody {
  code: string
  type: EquipmentType
  status: EquipmentStatus
  plateNumber: string | null
  powerHp: number | null
  serialNumber: string | null
  capacityKg: number | null
  toolType: ToolType | null
  stockQuantity: number | null
}

export interface UnavailabilityBody {
  from: string
  to: string
  reason: string | null
}

export interface WorkerFilters {
  active: '' | 'true' | 'false'
  page: number
}

export interface EquipmentFilters {
  type: EquipmentType | ''
  status: EquipmentStatus | ''
  page: number
}

function pageParams(page: number) {
  return new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
}

export function listWorkers({ active, page }: WorkerFilters) {
  const params = pageParams(page)
  if (active) params.set('active', active)
  return api<PageResponse<WorkerDto>>(`/workers?${params}`)
}
export const getWorker = (id: string) => api<WorkerDto>(`/workers/${id}`)
export const createWorker = (body: WorkerWriteBody) =>
  api<WorkerDto>('/workers', { method: 'POST', body: JSON.stringify(body) })
export const updateWorker = (id: string, body: WorkerWriteBody) =>
  api<WorkerDto>(`/workers/${id}`, { method: 'PUT', body: JSON.stringify(body) })
export const deleteWorker = (id: string) => api<void>(`/workers/${id}`, { method: 'DELETE' })
export const addWorkerUnavailability = (id: string, body: UnavailabilityBody) =>
  api<WorkerDto>(`/workers/${id}/unavailabilities`, { method: 'POST', body: JSON.stringify(body) })

export function listEquipment({ type, status, page }: EquipmentFilters) {
  const params = pageParams(page)
  if (type) params.set('type', type)
  if (status) params.set('status', status)
  return api<PageResponse<EquipmentDto>>(`/equipment?${params}`)
}
export const getEquipment = (id: string) => api<EquipmentDto>(`/equipment/${id}`)
export const createEquipment = (body: EquipmentWriteBody) =>
  api<EquipmentDto>('/equipment', { method: 'POST', body: JSON.stringify(body) })
export const updateEquipment = (id: string, body: EquipmentWriteBody) =>
  api<EquipmentDto>(`/equipment/${id}`, { method: 'PUT', body: JSON.stringify(body) })
export const deleteEquipment = (id: string) => api<void>(`/equipment/${id}`, { method: 'DELETE' })
export const addEquipmentUnavailability = (id: string, body: UnavailabilityBody) =>
  api<EquipmentDto>(`/equipment/${id}/unavailabilities`, { method: 'POST', body: JSON.stringify(body) })

// "tractor, picker" -> ["tractor", "picker"] (trimmed, no empty, no duplicate)
export function parseSkills(text: string): string[] {
  return [...new Set(text.split(',').map((s) => s.trim()).filter(Boolean))]
}

// Number of an input. Empty or not a number gives null, so the server reports the field.
export function parseNumber(text: string): number | null {
  if (text.trim() === '') return null
  const n = Number(text)
  return Number.isFinite(n) ? n : null
}

// "datetime-local" value (2026-11-20T08:00, local time) -> ISO instant. Null if empty or invalid.
export function toInstant(local: string): string | null {
  if (!local) return null
  const d = new Date(local)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

// Client-side check of the unavailability form. The server checks again (422).
export function unavailabilityError(from: string, to: string): string | null {
  const f = toInstant(from)
  const t = toInstant(to)
  if (!f || !t) return 'Choose a start and an end.'
  if (t <= f) return 'The end must be after the start.'
  return null
}

// What the list pages show for the kind-specific fields.
export function equipmentSummary(e: EquipmentDto): string {
  switch (e.type) {
    case 'TRACTOR':
      return `${e.plateNumber ?? '-'} · ${e.powerHp ?? '-'} hp`
    case 'BIN':
      return `${e.serialNumber ?? '-'} · ${e.capacityKg ?? '-'} kg`
    default:
      return `Stock: ${e.stockQuantity ?? '-'}`
  }
}
