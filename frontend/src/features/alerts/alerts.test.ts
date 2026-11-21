import { describe, expect, it } from 'vitest'
import {
  MAX_ALERT_PHOTO_BYTES,
  alertPhotoError,
  canCreateUrgentRequest,
  normalizeNearestOrchard,
  normalizeAlerts,
  type AlertPage,
  type FieldAlert,
} from './alerts'

const alert = (overrides: Partial<FieldAlert> = {}): FieldAlert => ({
  id: 'alert-1',
  type: 'MATURITY',
  severity: 'MEDIUM',
  status: 'NEW',
  source: 'FARMER',
  orchardId: 'orchard-1',
  orchardName: 'Henchir El Mezraa',
  farmerId: 'farmer-1',
  location: { type: 'Point', coordinates: [10.76, 34.8] },
  description: 'The olives are ready.',
  photoUrls: [],
  statusHistory: [],
  createdAt: '2026-11-21T09:00:00Z',
  ...overrides,
})

describe('alertPhotoError', () => {
  it('accepts jpeg and png photos below the API limit', () => {
    expect(alertPhotoError(new File(['photo'], 'leaf.jpg', { type: 'image/jpeg' }))).toBeNull()
    expect(alertPhotoError(new File(['photo'], 'leaf.png', { type: 'image/png' }))).toBeNull()
  })

  it('rejects unsupported photo types and oversized photos', () => {
    expect(alertPhotoError(new File(['photo'], 'leaf.webp', { type: 'image/webp' }))).toContain('JPEG ou PNG')
    const oversized = new File(['photo'], 'leaf.jpg', { type: 'image/jpeg' })
    Object.defineProperty(oversized, 'size', { value: MAX_ALERT_PHOTO_BYTES + 1 })
    expect(alertPhotoError(oversized)).toContain('5 Mo')
  })
})

describe('normalizeAlerts', () => {
  it('supports both the documented page response and a plain array', () => {
    const page: AlertPage = { content: [alert()], page: 0, size: 20, totalElements: 1 }
    expect(normalizeAlerts(page)).toEqual(page.content)
    expect(normalizeAlerts([alert({ id: 'alert-2' })])).toHaveLength(1)
  })
})

describe('normalizeNearestOrchard', () => {
  it('accepts the orchard directly or in the endpoint envelope', () => {
    const orchard = { id: 'orchard-1', name: 'Henchir El Mezraa' }
    expect(normalizeNearestOrchard(orchard)).toEqual(orchard)
    expect(normalizeNearestOrchard({ orchard })).toEqual(orchard)
    expect(normalizeNearestOrchard(null)).toBeNull()
  })
})

describe('canCreateUrgentRequest', () => {
  it('requires an acknowledged maturity alert', () => {
    expect(canCreateUrgentRequest(alert({ status: 'NEW' }))).toBe(false)
    expect(canCreateUrgentRequest(alert({ status: 'ACKNOWLEDGED' }))).toBe(true)
    expect(canCreateUrgentRequest(alert({ type: 'PEST', status: 'ACKNOWLEDGED' }))).toBe(false)
  })
})
