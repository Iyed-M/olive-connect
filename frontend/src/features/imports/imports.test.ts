import { describe, expect, it } from 'vitest'
import { errorsToCsv, parseNumber } from './imports'

describe('errorsToCsv', () => {
  it('writes a header and one line per rejected row', () => {
    expect(errorsToCsv([{ line: 3, field: 'lat', reason: 'lat 48.85 outside Tunisia' }])).toBe(
      'line,field,reason\n3,lat,lat 48.85 outside Tunisia',
    )
  })

  it('quotes cells that contain a comma or a quote', () => {
    const csv = errorsToCsv([{ line: 7, field: 'name', reason: 'bad "value", sorry' }])
    expect(csv).toBe('line,field,reason\n7,name,"bad ""value"", sorry"')
  })

  it('returns only the header when there is no error', () => {
    expect(errorsToCsv([])).toBe('line,field,reason')
  })
})

describe('parseNumber', () => {
  it('accepts a decimal comma and rejects empty or invalid text', () => {
    expect(parseNumber('12,5')).toBe(12.5)
    expect(parseNumber(' ')).toBeNull()
    expect(parseNumber('abc')).toBeNull()
  })
})
