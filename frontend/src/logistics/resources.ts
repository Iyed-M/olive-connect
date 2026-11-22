import { api } from '@/lib/api'
import { PAGE_SIZE, type PageResponse } from '@/admin/users'

// All API types of the Workers and Equipment pages live in this file. They mirror the backend
// DTOs of M4 (docs/04 classes Worker, Equipment, Tractor, Bin, Tool, Unavailability).
// If the backend field names change, adjust them here only.

// Same values as the Java EquipmentType, EquipmentStatus and ToolType enums.
export const EQUIPMENT_TYPE_LABEL = {
  TRACTOR: 'Tracteur',
  BIN: 'Benne',
  NET: 'Filet',
  SHAKER: 'Secoueur',
} as const
export type EquipmentType = keyof typeof EQUIPMENT_TYPE_LABEL

export const EQUIPMENT_STATUS_LABEL = {
  AVAILABLE: 'Disponible',
  MAINTENANCE: 'Maintenance',
  OUT_OF_ORDER: 'Hors service',
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
  createdAt: string
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
  corrections?: Correction[]
  createdAt: string
  version: number
}

// The body of POST and PUT: the fields of the other kinds are sent as null.
// toolType is not sent: the API derives it from the type.
export interface EquipmentWriteBody {
  code: string
  type: EquipmentType
  status: EquipmentStatus
  plateNumber: string | null
  powerHp: number | null
  serialNumber: string | null
  capacityKg: number | null
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
  if (!f || !t) return 'Choisissez un début et une fin.'
  if (t <= f) return 'La fin doit être postérieure au début.'
  return null
}

// What the list pages show for the kind-specific fields.
export function equipmentSummary(e: EquipmentDto): string {
  switch (e.type) {
    case 'TRACTOR':
      return `${e.plateNumber ?? '-'} · ${e.powerHp ?? '-'} ch`
    case 'BIN':
      return `${e.serialNumber ?? '-'} · ${e.capacityKg ?? '-'} kg`
    default:
      return `Stock : ${e.stockQuantity ?? '-'}`
  }
}

// ---- M12: calendar and supply orders (docs/06 GET /resources/calendar, /supply-orders) ----

export interface CalendarBooking {
  id: string
  roundId: string
  start: string
  end: string
}

export interface ResourceCalendarDto {
  resourceId: string
  resourceKind: 'WORKER' | 'EQUIPMENT'
  label: string
  bookings: CalendarBooking[]
  unavailabilities: Unavailability[]
}

export const getResourceCalendar = (resourceId: string, from: string, to: string) =>
  api<ResourceCalendarDto>(`/resources/calendar?${new URLSearchParams({ resourceId, from, to })}`)

// Monday 00:00 (local time) of the week of `date`.
export function startOfWeek(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

export interface DaySlot {
  label: string // "08:00 - 12:00" (clipped to the day)
  text: string
  kind: 'booking' | 'unavailable'
}

// What happens on one local day: bookings and unavailabilities overlapping it, clipped to the day.
export function daySlots(day: Date, cal: ResourceCalendarDto): DaySlot[] {
  const dayStart = day.getTime()
  const dayEnd = addDays(day, 1).getTime()
  const hhmm = (ms: number) =>
    new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  const slots: (DaySlot & { at: number })[] = []
  const add = (start: string, end: string, kind: DaySlot['kind'], text: string) => {
    const s = new Date(start).getTime()
    const e = new Date(end).getTime()
    if (s >= dayEnd || e <= dayStart) return
    const from = Math.max(s, dayStart)
    const to = Math.min(e, dayEnd)
    const toLabel = to === dayEnd ? '24:00' : hhmm(to)
    slots.push({ at: from, label: `${hhmm(from)} - ${toLabel}`, text, kind })
  }
  for (const b of cal.bookings) add(b.start, b.end, 'booking', 'Tournée')
  for (const u of cal.unavailabilities) add(u.from, u.to, 'unavailable', u.reason ?? 'Indisponible')
  return slots.sort((a, b) => a.at - b.at).map(({ label, text, kind }) => ({ label, text, kind }))
}

export const SUPPLY_STATUS_LABEL = { ORDERED: 'Commandée', RECEIVED: 'Reçue' } as const
export type SupplyStatus = keyof typeof SUPPLY_STATUS_LABEL

// Dates are ISO local dates (2026-11-21).
export interface SupplyOrderDto {
  id: string
  toolId: string
  toolCode: string | null
  toolType: ToolType | null
  quantity: number
  supplier: string
  orderedAt: string
  expectedAt: string | null
  receivedAt: string | null
  status: SupplyStatus
}

export interface SupplyOrderBody {
  toolId: string
  quantity: number
  supplier: string
  expectedAt: string | null
}

export function listSupplyOrders(status: SupplyStatus | '', page: number) {
  const params = pageParams(page)
  if (status) params.set('status', status)
  return api<PageResponse<SupplyOrderDto>>(`/supply-orders?${params}`)
}
export const createSupplyOrder = (body: SupplyOrderBody) =>
  api<SupplyOrderDto>('/supply-orders', { method: 'POST', body: JSON.stringify(body) })
export const receiveSupplyOrder = (id: string) =>
  api<SupplyOrderDto>(`/supply-orders/${id}/receive`, { method: 'POST' })
// NETs and SHAKERs: the tools of the supply order form.
export const listTools = (type: ToolType) => api<PageResponse<EquipmentDto>>(`/equipment?type=${type}&size=100`)

// One staff correction kept in the history of a document (FR-6.5).
export interface Correction {
  at: string
  byUserId: string
  comment: string
}
