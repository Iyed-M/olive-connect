import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import {
  dateRangeError,
  estimateForOrchard,
  requestErrorMessage,
  sortRequestsByPriority,
  type HarvestRequest,
} from './harvestRequests'

const request = (id: string, priorityScore: number): HarvestRequest => ({
  id,
  orchard: { id: `orchard-${id}`, name: `Orchard ${id}`, delegation: 'SAKIET_EZZIT' },
  type: 'PLANNED',
  desiredFrom: '2026-11-24',
  desiredTo: '2026-11-27',
  estimatedQuantityKg: 9000,
  comment: null,
  status: 'SUBMITTED',
  rejectionReason: null,
  priorityScore,
  roundId: null,
  createdAt: '2026-11-20T09:00:00Z',
  validatedAt: null,
  history: [],
  allowedActions: ['CANCEL', 'MODIFY'],
  version: 0,
})

describe('estimateForOrchard', () => {
  it('uses the last season yield when it is available', () => {
    expect(estimateForOrchard({ areaHa: 4.5, lastSeasonYieldKgHa: 2100 })).toBe(9450)
  })

  it('falls back to 1000 kg per hectare for a new orchard', () => {
    expect(estimateForOrchard({ areaHa: 2, lastSeasonYieldKgHa: 0 })).toBe(2000)
  })
})

describe('dateRangeError', () => {
  it('rejects an end date before the start date', () => {
    expect(dateRangeError('2026-11-27', '2026-11-24', '2026-11-20')).toMatch(/after/i)
  })

  it('rejects dates in the past and accepts a current range', () => {
    expect(dateRangeError('2026-11-19', '2026-11-21', '2026-11-20')).toMatch(/past/i)
    expect(dateRangeError('2026-11-24', '2026-11-27', '2026-11-20')).toBeNull()
    expect(dateRangeError('2026-11-20', '2026-11-20', '2026-11-20')).toBeNull()
  })
})

describe('sortRequestsByPriority', () => {
  it('returns a priority-descending copy for the manager queue', () => {
    const requests = [request('low', 10), request('urgent', 82.5), request('medium', 40)]
    expect(sortRequestsByPriority(requests).map(({ id }) => id)).toEqual(['urgent', 'medium', 'low'])
    expect(requests.map(({ id }) => id)).toEqual(['low', 'urgent', 'medium'])
  })
})

describe('requestErrorMessage', () => {
  it('explains that a stale request must be reloaded', () => {
    expect(requestErrorMessage(new ApiError(409, 'STALE_VERSION', 'stale'))).toMatch(/changed/i)
  })

  it('keeps the server detail for duplicate and general conflicts', () => {
    expect(requestErrorMessage(new ApiError(409, 'ACTIVE_REQUEST_EXISTS', 'already open'))).toBe('already open')
    expect(requestErrorMessage(new ApiError(500, 'INTERNAL_ERROR', 'try later'))).toBe('try later')
  })
})
