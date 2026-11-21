import { getAccessToken, getUser } from '@/auth/session'
import type { CollectionRound, RoundStop } from '@/features/collection/planning'
import { api, ApiError } from '@/lib/api'

export type QualityGrade = 'EXTRA' | 'GOOD' | 'AVERAGE' | 'POOR'

export const QUALITY_GRADE_LABEL: Record<QualityGrade, string> = {
  EXTRA: 'Extra',
  GOOD: 'Bonne',
  AVERAGE: 'Moyenne',
  POOR: 'Faible',
}

export interface BinOption {
  id: string
  code: string
  capacityKg: number | null
}

export type IncomingStop = RoundStop & { bins: BinOption[] }

export type IncomingRound = Omit<CollectionRound, 'stops'> & {
  stops: IncomingStop[]
  bins: BinOption[]
  season: string | null
}

export interface QualityAssessment {
  grade: QualityGrade | string
  defectRatePct: number | null
  leafRatePct: number | null
  estimatedOilYieldPct: number | null
}

export interface MillReception {
  id: string
  millId: string
  millName: string | null
  roundId: string
  binId: string
  binCode: string | null
  orchardId: string
  orchardName: string | null
  farmerId: string | null
  harvestRequestId: string | null
  grossWeightKg: number
  tareKg: number
  netWeightKg: number
  binCapacityKg: number
  fillRate: number
  quality: QualityAssessment
  season: string | null
  varietyCode: string | null
  delegation: string | null
  orchardAreaHa: number | null
  requestValidatedAt: string | null
  leadTimeHours: number | null
  receivedAt: string
}

export interface ReceptionPage {
  content: MillReception[]
  page: number
  size: number
  totalElements: number
}

export interface ReceptionFormValues {
  binId: string
  grossWeightKg: string
  tareKg: string
  quality: QualityGrade | ''
}

export interface ReceptionWriteBody {
  roundId: string
  binId: string
  orchardId: string
  grossWeightKg: number
  tareKg: number
  quality: {
    grade: QualityGrade
    defectRatePct: number
    leafRatePct: number
    estimatedOilYieldPct: number
  }
}

export interface ReceptionPreview {
  grossWeightKg: number
  tareKg: number
  netWeightKg: number
  fillRate: number | null
}

type ReceptionField = keyof ReceptionFormValues
type ReceptionFieldErrors = Partial<Record<ReceptionField, string>>

type RawIncomingStop = Omit<Partial<RoundStop>, 'bins' | 'status'> & {
  status?: unknown
  bins?: unknown
  binOptions?: unknown
  equipment?: unknown
  binId?: unknown
  binCode?: unknown
  binCapacityKg?: unknown
}

type RawIncomingRound = Omit<Partial<CollectionRound>, 'stops' | 'status'> & {
  status?: unknown
  season?: unknown
  stops?: RawIncomingStop[]
  bins?: unknown
  binOptions?: unknown
  equipment?: unknown
  binIds?: unknown
}

type RawIncomingResponse = RawIncomingRound[] | { content?: RawIncomingRound[] }

type RawReception = Omit<Partial<MillReception>, 'quality'> & {
  quality?: unknown
  mill?: unknown
  orchard?: unknown
  bin?: unknown
}

type RawReceptionResponse = RawReception[] | {
  content?: RawReception[]
  page?: number
  size?: number
  totalElements?: number
}

export function normalizeIncomingRounds(response: RawIncomingResponse): IncomingRound[] {
  const rounds = Array.isArray(response) ? response : response.content ?? []
  return rounds.map((rawRound) => {
    // The M7 incoming DTO exposes only the round's BIN metadata. Keep the
    // equipmentIds fallback for older payloads; the backend remains authoritative.
    const roundBins = normalizeBins(
      rawRound.bins ?? rawRound.binOptions ?? rawRound.equipment ?? rawRound.binIds ?? [],
    )
    const stops = (rawRound.stops ?? []).map((rawStop, index) => {
      const directBin = normalizeBins({
        id: rawStop.binId,
        code: rawStop.binCode,
        capacityKg: rawStop.binCapacityKg,
      })
      const stopBins = normalizeBins(rawStop.bins ?? rawStop.binOptions ?? rawStop.equipment)
      return {
        order: integerValue(rawStop.order, index + 1),
        harvestRequestId: stringValue(rawStop.harvestRequestId),
        orchardId: stringValue(rawStop.orchardId),
        farmerId: stringOrNull(rawStop.farmerId),
        orchardName: stringValue(rawStop.orchardName, 'Verger sans nom'),
        location: rawStop.location ?? null,
        plannedArrival: stringValue(rawStop.plannedArrival),
        estimatedKg: numberValue(rawStop.estimatedKg),
        status: stopStatus(rawStop.status),
        harvestedKg: numberOrNull(rawStop.harvestedKg),
        crateCount: integerOrNull(rawStop.crateCount),
        comment: stringOrNull(rawStop.comment),
        bins: stopBins.length > 0 ? stopBins : directBin.length > 0 ? directBin : roundBins,
      }
    })

    return {
      id: stringValue(rawRound.id),
      code: stringValue(rawRound.code, stringValue(rawRound.id)),
      date: stringValue(rawRound.date),
      startTime: stringValue(rawRound.startTime),
      endTime: stringValue(rawRound.endTime),
      status: roundStatus(rawRound.status),
      plannedById: stringOrNull(rawRound.plannedById),
      teamLeaderId: stringValue(rawRound.teamLeaderId),
      teamLeader: rawRound.teamLeader ?? null,
      millId: stringValue(rawRound.millId),
      mill: rawRound.mill ?? null,
      workerIds: stringArray(rawRound.workerIds),
      equipmentIds: stringArray(rawRound.equipmentIds),
      stops,
      estimatedTotalKg: numberValue(rawRound.estimatedTotalKg),
      totalHarvestedKg: numberValue(rawRound.totalHarvestedKg),
      version: integerValue(rawRound.version),
      bins: roundBins,
      season: stringOrNull(rawRound.season),
    }
  })
}

export function findIncomingStop(
  rounds: IncomingRound[],
  roundId: string,
  stopOrder: number,
): { round: IncomingRound; stop: IncomingStop } | null {
  const round = rounds.find((candidate) => candidate.id === roundId)
  const stop = round?.stops.find((candidate) => candidate.order === stopOrder)
  return round && stop ? { round, stop } : null
}

export function getBinsForStop(round: IncomingRound, stop: IncomingStop): BinOption[] {
  return stop.bins.length > 0 ? stop.bins : round.bins
}

export function calculateReceptionPreview(
  grossWeightKg: string | number,
  tareKg: string | number,
  binCapacityKg: number | null | undefined,
): ReceptionPreview | null {
  const gross = numberValue(grossWeightKg)
  const tare = numberValue(tareKg)
  if (!Number.isFinite(gross) || !Number.isFinite(tare)) return null
  const net = gross - tare
  const fillRate = binCapacityKg && binCapacityKg > 0 ? net / binCapacityKg : null
  return { grossWeightKg: gross, tareKg: tare, netWeightKg: net, fillRate }
}

export function buildReceptionPayload(
  values: ReceptionFormValues,
  roundId: string,
  orchardId: string,
): { body: ReceptionWriteBody | null; errors: ReceptionFieldErrors } {
  const errors: ReceptionFieldErrors = {}
  const binId = values.binId.trim()
  const grossWeightKg = Number(values.grossWeightKg.trim())
  const tareKg = Number(values.tareKg.trim())

  if (!binId) errors.binId = 'Choisissez une benne.'
  if (!Number.isFinite(grossWeightKg) || grossWeightKg <= 0) {
    errors.grossWeightKg = 'Le poids brut doit être supérieur à zéro.'
  }
  if (!Number.isFinite(tareKg) || tareKg < 0) {
    errors.tareKg = 'La tare doit être positive ou nulle.'
  }
  if (Number.isFinite(grossWeightKg) && Number.isFinite(tareKg) && grossWeightKg <= tareKg) {
    errors.grossWeightKg = 'Le poids brut doit être supérieur à la tare.'
  }
  if (!values.quality) errors.quality = 'Choisissez une qualité.'
  if (!roundId.trim()) errors.binId = 'La tournée sélectionnée est introuvable.'
  if (!orchardId.trim()) errors.binId = 'Le verger sélectionné est introuvable.'

  if (Object.keys(errors).length > 0 || !values.quality) return { body: null, errors }
  return {
    body: {
      roundId: roundId.trim(),
      binId,
      orchardId: orchardId.trim(),
      grossWeightKg,
      tareKg,
      quality: {
        grade: values.quality,
        defectRatePct: 0,
        leafRatePct: 0,
        estimatedOilYieldPct: 0,
      },
    },
    errors,
  }
}

export function normalizeReceptions(response: RawReceptionResponse): ReceptionPage {
  if (Array.isArray(response)) {
    const content = response.map(normalizeReception)
    return { content, page: 0, size: content.length, totalElements: content.length }
  }
  const content = (response.content ?? []).map(normalizeReception)
  return {
    content,
    page: response.page ?? 0,
    size: response.size ?? content.length,
    totalElements: response.totalElements ?? content.length,
  }
}

export function formatReceptionError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Le serveur est momentanément indisponible. Vérifiez votre connexion.'
  if (error.errors.length > 0) return error.errors.map(({ field, message }) => `${field} : ${message}`).join(' ')
  switch (error.code) {
    case 'FORBIDDEN':
      return 'Vous n’êtes pas responsable de ce moulin.'
    case 'ROUND_MILL_MISMATCH':
      return 'La tournée sélectionnée ne dessert pas ce moulin.'
    case 'ORCHARD_NOT_IN_ROUND':
      return 'Le verger sélectionné ne fait pas partie de cette tournée.'
    case 'BIN_NOT_IN_ROUND':
      return 'La benne sélectionnée ne fait pas partie de cette tournée.'
    case 'RECEPTION_EXISTS':
      return 'Cette pesée est déjà enregistrée pour cette benne et ce verger.'
    case 'INVALID_WEIGHING':
      return 'Vérifiez les poids saisis : le net doit être positif et rester dans la capacité de la benne.'
    case 'NOT_FOUND':
      return 'La tournée, la benne ou le moulin demandé est introuvable.'
    default:
      return error.detail || 'La réception n’a pas pu être enregistrée.'
  }
}

export function formatReceptionKg(value: number): string {
  return `${Math.round(value).toLocaleString('fr-FR')} kg`
}

export function formatReceptionRate(value: number): string {
  return `${(value * 100).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`
}

export function qualityGradeLabel(value: string): string {
  return QUALITY_GRADE_LABEL[value as QualityGrade] ?? value
}

/** The mill id is present in M7 JWT claims; the optional user field supports the M7 MeDto too. */
export function getAssignedMillId(): string | null {
  const user = getUser() as unknown as { assignedMillId?: unknown } | null
  if (typeof user?.assignedMillId === 'string' && user.assignedMillId.trim()) return user.assignedMillId

  const token = getAccessToken()
  if (!token) return null
  try {
    const encodedPayload = token.split('.')[1]
    if (!encodedPayload) return null
    const normalized = encodedPayload.replaceAll('-', '+').replaceAll('_', '/')
    const payload = JSON.parse(globalThis.atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))) as unknown
    if (!isRecord(payload) || typeof payload.millId !== 'string' || !payload.millId.trim()) return null
    return payload.millId
  } catch {
    return null
  }
}

export const listIncomingRounds = (millId: string) =>
  api<RawIncomingResponse>(`/mills/${encodeURIComponent(millId)}/incoming`).then(normalizeIncomingRounds)

export const recordReception = (millId: string, body: ReceptionWriteBody) =>
  api<MillReception>(`/mills/${encodeURIComponent(millId)}/receptions`, {
    method: 'POST',
    body: JSON.stringify(body),
  }).then(normalizeReception)

export const updateMillCapacity = (millId: string, availableCapacityKg: number) =>
  api<unknown>(`/mills/${encodeURIComponent(millId)}/capacity`, {
    method: 'PUT',
    body: JSON.stringify({ availableCapacityKg }),
  })

export function listReceptions(mine = true) {
  const params = new URLSearchParams({ mine: String(mine) })
  return api<RawReceptionResponse>(`/receptions?${params}`).then(normalizeReceptions)
}

function normalizeReception(raw: RawReception): MillReception {
  const quality = normalizeQuality(raw.quality)
  const mill = isRecord(raw.mill) ? raw.mill : null
  const orchard = isRecord(raw.orchard) ? raw.orchard : null
  const bin = isRecord(raw.bin) ? raw.bin : null
  return {
    id: stringValue(raw.id),
    millId: stringValue(raw.millId),
    millName: stringOrNull(raw.millName) ?? stringOrNull(mill?.name),
    roundId: stringValue(raw.roundId),
    binId: stringValue(raw.binId) || stringValue(bin?.id),
    binCode: stringOrNull(raw.binCode) ?? stringOrNull(bin?.code),
    orchardId: stringValue(raw.orchardId),
    orchardName: stringOrNull(raw.orchardName) ?? stringOrNull(orchard?.name),
    farmerId: stringOrNull(raw.farmerId),
    harvestRequestId: stringOrNull(raw.harvestRequestId),
    grossWeightKg: numberValue(raw.grossWeightKg),
    tareKg: numberValue(raw.tareKg),
    netWeightKg: numberValue(raw.netWeightKg),
    binCapacityKg: numberValue(raw.binCapacityKg),
    fillRate: numberValue(raw.fillRate),
    quality,
    season: stringOrNull(raw.season),
    varietyCode: stringOrNull(raw.varietyCode),
    delegation: stringOrNull(raw.delegation),
    orchardAreaHa: numberOrNull(raw.orchardAreaHa),
    requestValidatedAt: stringOrNull(raw.requestValidatedAt),
    leadTimeHours: numberOrNull(raw.leadTimeHours),
    receivedAt: stringValue(raw.receivedAt),
  }
}

function normalizeQuality(value: unknown): QualityAssessment {
  if (typeof value === 'string') return qualityWithGrade(value)
  if (!isRecord(value)) return qualityWithGrade('UNKNOWN')
  return {
    grade: typeof value.grade === 'string' ? value.grade : 'UNKNOWN',
    defectRatePct: numberOrNull(value.defectRatePct),
    leafRatePct: numberOrNull(value.leafRatePct),
    estimatedOilYieldPct: numberOrNull(value.estimatedOilYieldPct),
  }
}

function qualityWithGrade(grade: string): QualityAssessment {
  return { grade, defectRatePct: null, leafRatePct: null, estimatedOilYieldPct: null }
}

function normalizeBins(value: unknown): BinOption[] {
  const entries = Array.isArray(value) ? value : value == null ? [] : [value]
  const bins: BinOption[] = []
  for (const entry of entries) {
    if (typeof entry === 'string' && entry.trim()) {
      addBin(bins, { id: entry, code: entry, capacityKg: null })
      continue
    }
    if (!isRecord(entry)) continue
    if (typeof entry.type === 'string' && entry.type !== 'BIN') continue
    const id = stringValue(entry.id ?? entry.binId)
    if (!id) continue
    addBin(bins, {
      id,
      code: stringValue(entry.code ?? entry.binCode, id),
      capacityKg: numberOrNull(entry.capacityKg ?? entry.binCapacityKg ?? entry.capacity),
    })
  }
  return bins
}

function addBin(bins: BinOption[], candidate: BinOption) {
  if (!bins.some((bin) => bin.id === candidate.id)) bins.push(candidate)
}

function roundStatus(value: unknown): CollectionRound['status'] {
  return value === 'IN_PROGRESS' || value === 'COMPLETED' || value === 'CANCELLED' ? value : 'PLANNED'
}

function stopStatus(value: unknown): RoundStop['status'] {
  return value === 'IN_PROGRESS' || value === 'DONE' || value === 'SKIPPED' ? value : 'PENDING'
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []
}

function stringValue(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function stringOrNull(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null
}

function numberValue(value: unknown, fallback = 0): number {
  if (typeof value === 'number') return Number.isFinite(value) ? value : fallback
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : fallback
  }
  return fallback
}

function numberOrNull(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function integerValue(value: unknown, fallback = 0): number {
  const parsed = numberValue(value, fallback)
  return Number.isInteger(parsed) ? parsed : fallback
}

function integerOrNull(value: unknown): number | null {
  const parsed = numberOrNull(value)
  return parsed != null && Number.isInteger(parsed) ? parsed : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
