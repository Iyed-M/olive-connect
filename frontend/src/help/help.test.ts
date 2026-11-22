import { describe, expect, it } from 'vitest'
import type { Role } from '@/auth/types'
import { getHelpDocument, helpKeyForPath, parseHelpMarkdown } from './index'

describe('contextual help routing', () => {
  it('selects the most specific document for a nested route', () => {
    expect(helpKeyForPath('/farmer/requests/request-1?tab=history', 'FARMER')).toBe('farmer-requests')
    expect(helpKeyForPath('/farmer/orchards/new', 'FARMER')).toBe('farmer-orchards')
  })

  it('falls back to the active role when a route has no dedicated page', () => {
    expect(helpKeyForPath('/some/new/page', 'TEAM_LEADER')).toBe('team-leader')
    expect(helpKeyForPath('/some/new/page', 'ADMIN')).toBe('admin')
  })

  it('always returns readable content for an authenticated route', () => {
    const roles: Role[] = ['FARMER', 'COLLECTION_MANAGER', 'TEAM_LEADER', 'LOGISTICS_MANAGER', 'MILL_MANAGER', 'ADMIN']

    for (const role of roles) {
      const document = getHelpDocument('/unknown', role)
      expect(document.key).toBe(role === 'FARMER' ? 'farmer' : role.toLowerCase().replace('_', '-'))
      expect(document.markdown.trim().length).toBeGreaterThan(0)
    }
  })
})

describe('help Markdown parsing', () => {
  it('keeps headings, paragraphs, and bullet items as safe renderable blocks', () => {
    expect(parseHelpMarkdown('# Title\n\nUse this page.\n\n- First\n- Second')).toEqual([
      { type: 'heading', level: 1, text: 'Title' },
      { type: 'paragraph', text: 'Use this page.' },
      { type: 'list', items: ['First', 'Second'] },
    ])
  })
})
