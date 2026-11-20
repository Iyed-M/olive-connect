import { describe, expect, it } from 'vitest'
import { buildMapQuery, NO_FILTERS } from './publicMap'

describe('buildMapQuery', () => {
  it('sends only the bbox when no filter is set', () => {
    expect(buildMapQuery([10, 34, 11, 35], NO_FILTERS)).toBe('bbox=10%2C34%2C11%2C35')
  })

  it('adds the filters that are set', () => {
    const query = buildMapQuery([10, 34, 11, 35], { ...NO_FILTERS, variety: 'CHEMLALI', maturityMin: '2' })
    expect(query).toContain('variety=CHEMLALI')
    expect(query).toContain('maturityMin=2')
    expect(query).not.toContain('status')
  })
})
