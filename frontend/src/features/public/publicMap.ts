import { api } from '@/lib/api'

export type HarvestStatus = 'NOT_HARVESTED' | 'SCHEDULED' | 'IN_PROGRESS' | 'HARVESTED'

export const STATUS_LABEL: Record<HarvestStatus, string> = {
  NOT_HARVESTED: 'Not harvested',
  SCHEDULED: 'Scheduled',
  IN_PROGRESS: 'In progress',
  HARVESTED: 'Harvested',
}

// docs/08 §5: grey not harvested, blue scheduled, orange in progress, green harvested.
export const STATUS_COLOR: Record<HarvestStatus, string> = {
  NOT_HARVESTED: '#78716c',
  SCHEDULED: '#2563eb',
  IN_PROGRESS: '#ea580c',
  HARVESTED: '#16a34a',
}

// One feature of GET /map/public: a single orchard, or a server-side cluster (it has a count).
// There is no owner or other personal field in this payload.
export interface PublicFeature {
  type: 'Feature'
  geometry: { type: 'Point'; coordinates: [number, number] }
  properties: {
    id?: string
    name?: string
    mainVarietyCode?: string
    harvestStatus?: HarvestStatus
    currentMaturityIndex?: number
    count?: number
  }
}

export interface PublicFeatureCollection {
  type: 'FeatureCollection'
  features: PublicFeature[]
}

export interface MapFilters {
  variety: string
  status: string
  maturityMin: string
  maturityMax: string
}

export const NO_FILTERS: MapFilters = { variety: '', status: '', maturityMin: '', maturityMax: '' }

// bbox = [west, south, east, north]
export type Bbox = [number, number, number, number]

export function buildMapQuery(bbox: Bbox, filters: MapFilters): string {
  const params = new URLSearchParams({ bbox: bbox.join(',') })
  for (const [key, value] of Object.entries(filters)) {
    if (value !== '') params.set(key, value)
  }
  return params.toString()
}

export const getPublicMap = (bbox: Bbox, filters: MapFilters) =>
  api<PublicFeatureCollection>(`/map/public?${buildMapQuery(bbox, filters)}`)
