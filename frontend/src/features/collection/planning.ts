import { api, ApiError } from '@/lib/api'
import { sortRequestsByPriority, type HarvestRequest } from '@/features/farmer/harvestRequests'
import type { GeoPoint } from '@/features/farmer/orchards'
import type { Correction, EquipmentType } from '@/logistics/resources'

export type RoundStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type StopStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED'
export type RoundStopUpdateStatus = Extract<StopStatus, 'IN_PROGRESS' | 'DONE' | 'SKIPPED'>
export type ResourceKind = 'WORKER' | 'EQUIPMENT'

export const ROUND_STATUS_LABEL: Record<RoundStatus, string> = {
  PLANNED: 'Planifiée',
  IN_PROGRESS: 'En cours',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
}

export const STOP_STATUS_LABEL: Record<StopStatus, string> = {
  PENDING: 'En attente',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
  SKIPPED: 'Ignorée',
}

export interface TeamLeaderOption {
  id: string
  firstName?: string | null
  lastName?: string | null
  name?: string | null
  available?: boolean
}

export interface MillDto {
  id: string
  name: string
  location?: GeoPoint | null
  address?: string | null
  dailyCapacityKg?: number | null
  availableCapacityKg: number
  capacityUpdatedAt?: string | null
}

export interface ResourceAvailability {
  id: string
  resourceKind: ResourceKind
  resourceType?: EquipmentType | string | null
  code?: string | null
  label?: string | null
  firstName?: string | null
  lastName?: string | null
  status?: string | null
  available: boolean
  capacityKg?: number | null
  reason?: string | null
}

export interface ResourceAvailabilityResponse {
  workers: ResourceAvailability[]
  equipment: ResourceAvailability[]
}

interface RawResourceAvailability {
  id: string
  resourceKind: ResourceKind
  resourceType?: string | null
  code?: string | null
  label?: string | null
  firstName?: string | null
  lastName?: string | null
  status?: string | null
  available: boolean
  capacityKg?: number | null
  reason?: string | null
}

interface RawAvailabilityResponse {
  workers: RawResourceAvailability[]
  equipment: RawResourceAvailability[]
}

export interface RoundStop {
  order: number
  harvestRequestId: string
  orchardId: string
  farmerId?: string | null
  orchardName: string
  location?: GeoPoint | null
  plannedArrival: string
  estimatedKg: number
  status: StopStatus
  harvestedKg: number | null
  crateCount: number | null
  comment?: string | null
}

export interface RoundBin {
  id: string
  code: string
  capacityKg: number | null
}

export interface RoundStopUpdateBody {
  status: RoundStopUpdateStatus
  harvestedKg?: number | null
  crateCount?: number | null
  comment?: string | null
  version?: number
}

export interface CollectionRound {
  id: string
  code: string
  date: string
  startTime: string
  endTime: string
  status: RoundStatus
  plannedById?: string | null
  teamLeaderId: string
  teamLeader?: TeamLeaderOption | null
  millId: string
  mill?: MillDto | null
  workerIds: string[]
  equipmentIds: string[]
  stops: RoundStop[]
  estimatedTotalKg: number
  totalHarvestedKg: number
  corrections?: Correction[]
  version: number
  bins?: RoundBin[]
}

export interface RoundWarning {
  code: string
  message?: string | null
  detail?: string | null
}

export interface PlanningGroup {
  requestIds: string[]
  estimatedTotalKg: number
  nearestMillId?: string | null
}

export interface PlanningSuggestions {
  date: string
  requests: HarvestRequest[]
  groups: PlanningGroup[]
  teamLeaders: TeamLeaderOption[]
  availability: ResourceAvailabilityResponse
  mills: MillDto[]
  warnings: RoundWarning[]
}

interface RawPlanningSuggestions {
  date?: string
  requests?: HarvestRequest[]
  validatedRequests?: HarvestRequest[]
  groups?: Array<Omit<PlanningGroup, 'requestIds'> & { requestIds?: string[]; requests?: HarvestRequest[] }>
  availableWorkers?: RawResourceAvailability[]
  availableEquipment?: RawResourceAvailability[]
  teamLeaders?: TeamLeaderOption[]
  mills?: MillDto[]
  warnings?: Array<RoundWarning | string>
}

export interface RoundWriteValues {
  date: string
  startTime: string
  endTime: string
  teamLeaderId: string
  workerIds: string[]
  equipmentIds: string[]
  millId: string
  requestIds: string[]
}

export interface RoundWriteBody extends RoundWriteValues {
  version?: number
}

export interface RoundMutationResult {
  round: CollectionRound
  warnings: RoundWarning[]
}

type RoundMutationResponse =
  | (CollectionRound & { warnings?: Array<RoundWarning | string> })
  | { round: CollectionRound; warnings?: Array<RoundWarning | string> }

export interface RoundPage {
  content: CollectionRound[]
  page: number
  size: number
  totalElements: number
}

export interface RoundFilters {
  from?: string
  to?: string
  status?: RoundStatus
  teamLeaderId?: string
}

export function sortValidatedRequests(requests: HarvestRequest[]): HarvestRequest[] {
  return sortRequestsByPriority(requests.filter((request) => request.status === 'VALIDATED'))
}

/** Round LocalTime values are interpreted as UTC by the API and bookings. */
export function toAvailabilityRange(
  date: string,
  startTime: string,
  endTime: string,
): { start: string; end: string } | null {
  if (!date || !startTime || !endTime) return null
  const start = new Date(`${date}T${startTime}:00Z`)
  const end = new Date(`${date}T${endTime}:00Z`)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) return null
  return { start: start.toISOString(), end: end.toISOString() }
}

export function buildRoundPayload(values: RoundWriteValues): RoundWriteBody {
  return {
    date: values.date,
    startTime: values.startTime.trim(),
    endTime: values.endTime.trim(),
    teamLeaderId: values.teamLeaderId.trim(),
    workerIds: uniqueNonEmpty(values.workerIds),
    equipmentIds: uniqueNonEmpty(values.equipmentIds),
    millId: values.millId.trim(),
    requestIds: uniqueNonEmpty(values.requestIds),
  }
}

export function normalizeAvailability(response: RawAvailabilityResponse): ResourceAvailabilityResponse {
  return {
    workers: response.workers.map(normalizeResource),
    equipment: response.equipment.map(normalizeResource),
  }
}

export function normalizePlanningSuggestions(response: RawPlanningSuggestions): PlanningSuggestions {
  const mills = response.mills ?? []
  const groups = (response.groups ?? []).map((group) => ({
    requestIds: group.requestIds ?? group.requests?.map((request) => request.id) ?? [],
    estimatedTotalKg: group.estimatedTotalKg,
    nearestMillId: group.nearestMillId,
  }))
  const availability = normalizeAvailability({
    workers: response.availableWorkers ?? [],
    equipment: response.availableEquipment ?? [],
  })
  return {
    date: response.date ?? '',
    requests: response.requests ?? response.validatedRequests ?? [],
    groups,
    teamLeaders: response.teamLeaders ?? [],
    availability,
    mills,
    warnings: normalizeWarnings(response.warnings ?? []),
  }
}

export function normalizeMills(response: MillDto[] | { content?: MillDto[] }): MillDto[] {
  return Array.isArray(response) ? response : response.content ?? []
}

export function normalizeRoundPage(response: CollectionRound[] | { content?: CollectionRound[]; page?: number; size?: number; totalElements?: number }): RoundPage {
  if (Array.isArray(response)) {
    return { content: response, page: 0, size: response.length, totalElements: response.length }
  }
  const content = response.content ?? []
  return {
    content,
    page: response.page ?? 0,
    size: response.size ?? content.length,
    totalElements: response.totalElements ?? content.length,
  }
}

export function normalizeRoundMutation(
  response: RoundMutationResponse,
): RoundMutationResult {
  if ('round' in response) {
    return { round: response.round, warnings: normalizeWarnings(response.warnings ?? []) }
  }
  return { round: response, warnings: normalizeWarnings(response.warnings ?? []) }
}

export function formatRoundWarning(warning: RoundWarning): string {
  const message = warning.code === 'MILL_CAPACITY_LOW'
    ? 'Le moulin sélectionné a moins de capacité disponible que l’estimation.'
    : warning.message ?? warning.detail ?? defaultWarningMessage(warning.code)
  return `${warning.code}: ${message}`
}

export function planningErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'La tournée n’a pas pu être enregistrée. Vérifiez la connexion puis réessayez.'
  if (error.code === 'BOOKING_CONFLICT') return `Conflit de réservation : ${error.detail || 'une ressource sélectionnée est déjà réservée.'}`
  if (error.code === 'STALE_VERSION') return 'Cette tournée a changé pendant votre modification. Rechargez-la avant de réessayer.'
  if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) {
    return error.errors.map(({ field, message }) => `${field}: ${message}`).join(' · ')
  }
  return error.detail || 'La tournée n’a pas pu être enregistrée.'
}

export function millCapacityWarning(estimatedKg: number, mill: MillDto | undefined): RoundWarning | null {
  if (!mill || estimatedKg <= mill.availableCapacityKg) return null
  return {
    code: 'MILL_CAPACITY_LOW',
    message: `L’estimation (${formatKg(estimatedKg)}) dépasse la capacité disponible de ${mill.name} (${formatKg(mill.availableCapacityKg)}).`,
  }
}

export function requestLocation(request: HarvestRequest): GeoPoint | null {
  const orchard = request.orchard as HarvestRequest['orchard'] & { location?: GeoPoint | null }
  return orchard.location ?? null
}

export function resourceLabel(resource: ResourceAvailability): string {
  if (resource.label) return resource.label
  if (resource.resourceKind === 'WORKER') {
    const name = [resource.firstName, resource.lastName].filter(Boolean).join(' ')
    return name || resource.id
  }
  return resource.code || resource.id
}

export function resourceAvailabilityLabel(resource: ResourceAvailability): string {
  if (resource.available) return 'Libre'
  switch (resource.reason) {
    case 'Booked':
      return 'Réservée'
    case 'Inactive':
      return 'Inactive'
    case 'Unavailable':
      return 'Indisponible'
    case 'MAINTENANCE':
      return 'En maintenance'
    case 'OUT_OF_ORDER':
      return 'Hors service'
    default:
      return resource.reason ?? 'Occupée'
  }
}

export function teamLeaderLabel(teamLeader: TeamLeaderOption): string {
  return teamLeader.name || [teamLeader.firstName, teamLeader.lastName].filter(Boolean).join(' ') || teamLeader.id
}

export function roundDateLabel(date: string): string {
  const parsed = new Date(`${date}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? date : new Intl.DateTimeFormat('fr-TN', { dateStyle: 'medium' }).format(parsed)
}

export function formatKg(value: number): string {
  return `${Math.round(value).toLocaleString()} kg`
}

export interface RoundSelectionContext {
  requests: HarvestRequest[]
  teamLeaders: TeamLeaderOption[]
  availability?: ResourceAvailabilityResponse
  mills: MillDto[]
  existingRequestIds?: string[]
  existingResourceIds?: string[]
  existingTeamLeaderId?: string
}

/** Keep stale selections visible and block submit until the user fixes them. */
export function validateRoundSelection(values: RoundWriteValues, context: RoundSelectionContext): string[] {
  const errors: string[] = []
  const existingRequests = new Set(context.existingRequestIds ?? [])
  const existingResources = new Set(context.existingResourceIds ?? [])

  if (values.requestIds.length === 0) errors.push('Sélectionnez au moins une demande validée.')
  for (const id of uniqueNonEmpty(values.requestIds)) {
    const request = context.requests.find((candidate) => candidate.id === id)
    if (!request && !existingRequests.has(id)) errors.push(`La demande ${id} n’est plus disponible.`)
    else if (request && request.status !== 'VALIDATED' && !existingRequests.has(id)) errors.push(`La demande ${request.id} n’est plus validée.`)
  }

  if (!values.teamLeaderId.trim()) errors.push('Choisissez un chef d’équipe.')
  else {
    const leader = context.teamLeaders.find((candidate) => candidate.id === values.teamLeaderId.trim())
    if (!leader && values.teamLeaderId.trim() !== context.existingTeamLeaderId) errors.push('Le chef d’équipe sélectionné n’est plus disponible.')
    else if (leader?.available === false && leader.id !== context.existingTeamLeaderId) errors.push('Le chef d’équipe sélectionné est occupé sur ce créneau.')
  }

  if (values.workerIds.length === 0) errors.push('Choisissez au moins un ouvrier.')
  if (values.equipmentIds.length === 0) errors.push('Choisissez au moins un équipement ou bac.')
  if (!values.millId.trim()) errors.push('Choisissez un moulin de destination.')
  else if (!context.mills.some((mill) => mill.id === values.millId.trim())) errors.push('Le moulin sélectionné n’est plus disponible.')

  if (context.availability) {
    const available = new Map([...context.availability.workers, ...context.availability.equipment].map((resource) => [resource.id, resource]))
    for (const id of [...uniqueNonEmpty(values.workerIds), ...uniqueNonEmpty(values.equipmentIds)]) {
      const resource = available.get(id)
      if (!resource && !existingResources.has(id)) errors.push(`La ressource ${id} n’est plus disponible.`)
      else if (resource && !resource.available && !existingResources.has(id)) errors.push(`${resourceLabel(resource)} est occupé(e) sur ce créneau.`)
    }
  }

  return errors
}

export function getPlanningSuggestions(date: string) {
  const params = new URLSearchParams({ date })
  return api<RawPlanningSuggestions>(`/planning/suggestions?${params}`).then(normalizePlanningSuggestions)
}

export function getResourceAvailability({ start, end, type }: { start: string; end: string; type?: string }) {
  const params = new URLSearchParams({ start, end })
  if (type) params.set('type', type)
  return api<RawAvailabilityResponse>(`/resources/availability?${params}`).then(normalizeAvailability)
}

export const listMills = () => api<MillDto[] | { content?: MillDto[] }>('/mills').then(normalizeMills)

export function listRounds(filters: RoundFilters = {}) {
  const params = new URLSearchParams()
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.status) params.set('status', filters.status)
  if (filters.teamLeaderId) params.set('teamLeaderId', filters.teamLeaderId)
  const query = params.toString()
  return api<CollectionRound[] | { content?: CollectionRound[]; page?: number; size?: number; totalElements?: number }>(`/rounds${query ? `?${query}` : ''}`).then(normalizeRoundPage)
}

export const correctRound = (id: string, body: { totalHarvestedKg: number; comment: string }) =>
  api<CollectionRound>(`/rounds/${id}/correction`, { method: 'PATCH', body: JSON.stringify(body) })

export const getRound = (id: string) => api<CollectionRound>(`/rounds/${id}`)

export const startRound = (id: string) =>
  api<CollectionRound>(`/rounds/${id}/start`, { method: 'POST' })

export const updateRoundStop = (id: string, order: number, body: RoundStopUpdateBody) =>
  api<CollectionRound>(`/rounds/${id}/stops/${order}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })

export const completeRound = (id: string) =>
  api<CollectionRound>(`/rounds/${id}/complete`, { method: 'POST' })

export const createRound = (body: RoundWriteBody) =>
  api<RoundMutationResponse>('/rounds', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then(normalizeRoundMutation)

export const updateRound = (id: string, body: RoundWriteBody) =>
  api<RoundMutationResponse>(`/rounds/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  }).then(normalizeRoundMutation)

export const cancelRound = (id: string) => api<void>(`/rounds/${id}/cancel`, { method: 'POST' })

function normalizeResource(entry: RawResourceAvailability): ResourceAvailability {
  return {
    id: entry.id,
    resourceKind: entry.resourceKind,
    resourceType: entry.resourceType,
    code: entry.code,
    label: entry.label,
    firstName: entry.firstName,
    lastName: entry.lastName,
    status: entry.status,
    available: entry.available,
    capacityKg: entry.capacityKg,
    reason: entry.reason,
  }
}

function normalizeWarnings(warnings: Array<RoundWarning | string>): RoundWarning[] {
  return warnings.map((warning) => typeof warning === 'string' ? { code: warning, message: defaultWarningMessage(warning) } : warning)
}

function defaultWarningMessage(code: string): string {
  switch (code) {
    case 'MILL_CAPACITY_LOW':
      return 'Le moulin sélectionné a moins de capacité disponible que l’estimation.'
    default:
      return 'Vérifiez cet avertissement de planification avant de continuer.'
  }
}

function uniqueNonEmpty(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))]
}
