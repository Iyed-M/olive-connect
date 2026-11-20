import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('accepts app paths, with query string', () => {
    expect(safeRedirect('/admin/users')).toBe('/admin/users')
    expect(safeRedirect('/farmer?tab=1')).toBe('/farmer?tab=1')
  })
  it('rejects other sites', () => {
    expect(safeRedirect('//evil.com')).toBeNull()
    expect(safeRedirect('https://evil.com')).toBeNull()
    expect(safeRedirect('/\\evil.com')).toBeNull()
  })
  it('rejects relative paths, empty and non-strings', () => {
    expect(safeRedirect('admin/users')).toBeNull()
    expect(safeRedirect('')).toBeNull()
    expect(safeRedirect(undefined)).toBeNull()
    expect(safeRedirect(42)).toBeNull()
  })
})
