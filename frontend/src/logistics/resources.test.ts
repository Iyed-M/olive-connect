import { describe, expect, it } from 'vitest'
import { equipmentSummary, parseNumber, parseSkills, toInstant, unavailabilityError, type EquipmentDto } from './resources'

describe('parseSkills', () => {
  it('trims and drops empty and duplicate skills', () => {
    expect(parseSkills(' picking, , driving,picking ')).toEqual(['picking', 'driving'])
  })
})

describe('parseNumber', () => {
  it('returns null for empty or invalid text', () => {
    expect(parseNumber('')).toBeNull()
    expect(parseNumber('abc')).toBeNull()
    expect(parseNumber('12.5')).toBe(12.5)
  })
})

describe('unavailabilityError', () => {
  it('requires both dates', () => {
    expect(unavailabilityError('', '2026-11-21T08:00')).toMatch(/Choose/)
  })
  it('requires the end after the start', () => {
    expect(unavailabilityError('2026-11-21T08:00', '2026-11-21T08:00')).toMatch(/after/)
    expect(unavailabilityError('2026-11-22T08:00', '2026-11-21T08:00')).toMatch(/after/)
  })
  it('accepts a valid range', () => {
    expect(unavailabilityError('2026-11-21T08:00', '2026-11-22T08:00')).toBeNull()
  })
})

describe('toInstant', () => {
  it('gives an ISO instant or null', () => {
    expect(toInstant('2026-11-21T08:00')).toMatch(/^2026-11-2\dT\d\d:00:00.000Z$/)
    expect(toInstant('')).toBeNull()
  })
})

describe('equipmentSummary', () => {
  const base: EquipmentDto = {
    id: '1',
    code: 'X',
    type: 'TRACTOR',
    status: 'AVAILABLE',
    plateNumber: '123 TUN 4567',
    powerHp: 90,
    serialNumber: null,
    capacityKg: null,
    toolType: null,
    stockQuantity: null,
    unavailabilities: [],
    createdAt: '2026-11-20T09:00:00Z',
    version: 0,
  }
  it('depends on the type', () => {
    expect(equipmentSummary(base)).toBe('123 TUN 4567 · 90 hp')
    expect(equipmentSummary({ ...base, type: 'BIN', serialNumber: 'S1', capacityKg: 500 })).toBe('S1 · 500 kg')
    expect(equipmentSummary({ ...base, type: 'NET', stockQuantity: 4 })).toBe('Stock: 4')
  })
})
