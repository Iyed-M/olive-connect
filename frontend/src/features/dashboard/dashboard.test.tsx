import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { NAV } from '@/auth/nav'
import {
  DASHBOARD_CHART_GRID_CLASS,
  DASHBOARD_FILTER_GRID_CLASS,
  DASHBOARD_KPI_GRID_CLASS,
  DASHBOARD_PAGE_CLASS,
  DashboardStateView,
} from './DashboardPage'
import {
  buildDashboardQuery,
  buildMillVolumesQuery,
  dashboardState,
  normalizeDashboardSummary,
  normalizeFillRatePoints,
  normalizeLeadTimePoints,
  normalizeMillVolumePoints,
  normalizeYieldPoints,
  type DashboardFilters,
} from './dashboard'

const filters: DashboardFilters = {
  season: '2026-2027',
  period: 'season',
  variety: 'CHEMLALI',
  delegation: 'SAKIET_EZZIT',
}

describe('dashboard query construction', () => {
  it('sends the season, dimension filters, and requested grouping to the documented endpoint', () => {
    expect(buildDashboardQuery(filters, { groupBy: 'delegation' })).toBe(
      '?season=2026-2027&variety=CHEMLALI&delegation=SAKIET_EZZIT&groupBy=delegation',
    )
  })

  it('turns a rolling period into API date bounds', () => {
    expect(
      buildDashboardQuery({ ...filters, period: 'last-7-days' }, { now: new Date('2026-11-21T12:00:00Z') }),
    ).toBe(
      '?season=2026-2027&variety=CHEMLALI&delegation=SAKIET_EZZIT&from=2026-11-14T12%3A00%3A00.000Z&to=2026-11-21T12%3A00%3A00.000Z',
    )
  })

  it('gives the mill-volume endpoint the full July-to-July season bounds', () => {
    expect(buildMillVolumesQuery(filters)).toBe(
      '?season=2026-2027&variety=CHEMLALI&delegation=SAKIET_EZZIT&from=2026-07-01T00%3A00%3A00.000Z&to=2027-07-01T00%3A00%3A00.000Z',
    )
  })
})

describe('dashboard DTO normalization', () => {
  it('normalizes the summary values used by the KPI tiles', () => {
    expect(
      normalizeDashboardSummary({
        totalNetKg: 12_400,
        avgYieldKgHa: 2_175.5,
        avgLeadTimeH: 40.25,
        avgFillRate: 0.825,
      }),
    ).toEqual({
      seasonTonnageKg: 12_400,
      avgYieldKgHa: 2_175.5,
      avgLeadTimeHours: 40.25,
      avgFillRate: 0.825,
    })
  })

  it('keeps the dimensions and values from the aggregation series', () => {
    expect(
      normalizeYieldPoints([
        { orchardId: 'orchard-1', orchardName: 'Henchir El Mezraa', delegation: 'SAKIET_EZZIT', yieldKgHa: 2_400 },
      ]),
    ).toEqual([
      {
        label: 'Henchir El Mezraa',
        orchardId: 'orchard-1',
        orchardName: 'Henchir El Mezraa',
        delegation: 'SAKIET_EZZIT',
        yieldKgHa: 2_400,
      },
    ])

    expect(normalizeLeadTimePoints([{ _id: 'AGAREB', avgLeadTimeH: 24, p90LeadTimeH: 48 }])).toEqual([
      { label: 'AGAREB', avgLeadTimeHours: 24, p90LeadTimeHours: 48 },
    ])

    expect(normalizeFillRatePoints([{ _id: { week: 47, year: 2026 }, avgFillRate: 0.72, receptions: 12 }])).toEqual([
      { label: '2026-W47', avgFillRate: 0.72, receptions: 12 },
    ])

    expect(
      normalizeMillVolumePoints([{ date: '2026-11-21', millId: 'mill-1', totalNetKg: 3_200 }]),
    ).toEqual([{ date: '2026-11-21', millName: 'mill-1', volumeKg: 3_200 }])
  })
})

describe('dashboard render state', () => {
  it('links the dashboard from every role that is allowed to use it', () => {
    expect(NAV.COLLECTION_MANAGER.some((item) => item.to === '/dashboard')).toBe(true)
    expect(NAV.MILL_MANAGER.some((item) => item.to === '/dashboard')).toBe(true)
    expect(NAV.ADMIN.some((item) => item.to === '/dashboard')).toBe(true)
  })

  it('gives loading, API-error, empty, and ready their own states', () => {
    expect(dashboardState({ loading: true, error: null, hasData: false })).toBe('loading')
    expect(dashboardState({ loading: false, error: new Error('down'), hasData: false })).toBe('error')
    expect(dashboardState({ loading: false, error: null, hasData: false })).toBe('empty')
    expect(dashboardState({ loading: false, error: null, hasData: true })).toBe('ready')
  })

  it('renders distinct loading, empty, and API-error messages', () => {
    expect(renderToStaticMarkup(<DashboardStateView state="loading" />)).toMatch(/Loading dashboard/)
    expect(renderToStaticMarkup(<DashboardStateView state="empty" />)).toMatch(/No dashboard data/)
    expect(renderToStaticMarkup(<DashboardStateView state="error" error={new Error('API down')} />)).toMatch(/could not be loaded/)
  })

  it('keeps the dashboard layout usable at the 360px mobile baseline', () => {
    expect(DASHBOARD_PAGE_CLASS).toContain('min-w-0')
    expect(DASHBOARD_FILTER_GRID_CLASS).toContain('grid-cols-1')
    expect(DASHBOARD_KPI_GRID_CLASS).toContain('grid-cols-2')
    expect(DASHBOARD_CHART_GRID_CLASS).toContain('grid-cols-1')
  })
})
