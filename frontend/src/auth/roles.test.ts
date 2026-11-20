import { describe, expect, it } from 'vitest'
import { activeRole, priorityRole } from './roles'

describe('priorityRole', () => {
  it('returns the only role', () => {
    expect(priorityRole(['FARMER'])).toBe('FARMER')
  })
  it('puts COLLECTION_MANAGER before TEAM_LEADER', () => {
    expect(priorityRole(['TEAM_LEADER', 'COLLECTION_MANAGER'])).toBe('COLLECTION_MANAGER')
  })
  it('puts ADMIN before FARMER', () => {
    expect(priorityRole(['FARMER', 'ADMIN'])).toBe('ADMIN')
  })
  it('returns ADMIN when the User has every role', () => {
    expect(
      priorityRole(['FARMER', 'TEAM_LEADER', 'LOGISTICS_MANAGER', 'MILL_MANAGER', 'COLLECTION_MANAGER', 'ADMIN']),
    ).toBe('ADMIN')
  })
  it('orders MILL_MANAGER > LOGISTICS_MANAGER > TEAM_LEADER', () => {
    expect(priorityRole(['TEAM_LEADER', 'LOGISTICS_MANAGER', 'MILL_MANAGER'])).toBe('MILL_MANAGER')
    expect(priorityRole(['TEAM_LEADER', 'LOGISTICS_MANAGER'])).toBe('LOGISTICS_MANAGER')
  })
})

describe('activeRole', () => {
  const roles = ['TEAM_LEADER', 'FARMER'] as const

  it('uses the stored role when the User holds it', () => {
    expect(activeRole([...roles], 'FARMER')).toBe('FARMER')
  })
  it('falls back to the priority role when nothing is stored', () => {
    expect(activeRole([...roles], null)).toBe('TEAM_LEADER')
  })
  it('falls back when the stored role is not held', () => {
    expect(activeRole([...roles], 'ADMIN')).toBe('TEAM_LEADER')
  })
  it('falls back when the stored value is garbage', () => {
    expect(activeRole([...roles], 'banana')).toBe('TEAM_LEADER')
    expect(activeRole([...roles], '')).toBe('TEAM_LEADER')
  })
})
