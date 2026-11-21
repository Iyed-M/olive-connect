import { api } from '@/lib/api'
import type { Correction } from '@/logistics/resources'

export interface GeoPoint {
  type: 'Point'
  coordinates: [number, number]
}

export interface Plot {
  code: string
  varietyCode: string
  areaHa: number
  treeCount: number
  plantingYear: number
  irrigated: boolean
}

export interface Orchard {
  id: string
  name: string
  ownerId: string
  owner: { displayName?: string }
  location: GeoPoint
  delegation: string
  plots: Plot[]
  areaHa: number
  treeCount: number
  mainVarietyCode: string
  harvestStatus: 'NOT_HARVESTED' | 'SCHEDULED' | 'IN_PROGRESS' | 'HARVESTED'
  currentMaturityIndex: number
  maturityUpdatedAt: string | null
  lastSeasonYieldKgHa: number
  corrections?: Correction[]
  version: number | null
}

export interface OrchardWriteBody {
  name: string
  delegation: string
  location: GeoPoint
  plots: Plot[]
}

export interface OliveVariety {
  code: string
  name: string
  avgOilYieldPct: number
  optimalMaturityMin: number
  optimalMaturityMax: number
}

export const listOrchards = () => api<Orchard[]>('/orchards')

export const getOrchard = (id: string) => api<Orchard>(`/orchards/${id}`)

export const createOrchard = (body: OrchardWriteBody) =>
  api<Orchard>('/orchards', { method: 'POST', body: JSON.stringify(body) })

export const updateOrchard = (id: string, body: OrchardWriteBody) =>
  api<Orchard>(`/orchards/${id}`, { method: 'PUT', body: JSON.stringify(body) })

export const deleteOrchard = (id: string) => api<void>(`/orchards/${id}`, { method: 'DELETE' })

export const listVarieties = () => api<OliveVariety[]>('/varieties')
