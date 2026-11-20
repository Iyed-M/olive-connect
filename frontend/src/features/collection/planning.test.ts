import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import type { HarvestRequest } from '@/features/farmer/harvestRequests'
import {
  buildRoundPayload,
  formatRoundWarning,
  normalizeAvailability,
  normalizePlanningSuggestions,
  normalizeRoundMutation,
  normalizeRoundPage,
  planningErrorMessage,
  sortValidatedRequests,
  validateRoundSelection,
  type CollectionRound,
  toAvailabilityRange,
  type RoundWarning,
} from './planning'

const request = (id: string, priorityScore: number, createdAt: string, status: 'VALIDATED' | 'SCHEDULED' = 'VALIDATED') => ({
  id,
  priorityScore,
  createdAt,
  status,
}) as Parameters<typeof sortValidatedRequests>[0][number]

describe('sortValidatedRequests', () => {
  it('keeps validated requests in priority order and leaves scheduled requests out', () => {
    expect(
      sortValidatedRequests([
        request('old', 50, '2026-11-20T09:00:00Z'),
        request('urgent', 90, '2026-11-20T08:00:00Z'),
        request('scheduled', 100, '2026-11-20T07:00:00Z', 'SCHEDULED'),
        request('tie-newer', 50, '2026-11-21T09:00:00Z'),
      ]).map(({ id }) => id),
    ).toEqual(['urgent', 'tie-newer', 'old'])
  })
})

describe('round time and payload helpers', () => {
  it('converts the UTC round slot to ISO availability bounds', () => {
    const range = toAvailabilityRange('2026-11-25', '07:00', '15:00')
    expect(range).not.toBeNull()
    expect(range ? range.start < range.end : false).toBe(true)
    expect(range?.start).toBe('2026-11-25T07:00:00.000Z')
  })

  it('builds a stable API payload without persisting stale form text', () => {
    expect(
      buildRoundPayload({
        date: '2026-11-25',
        startTime: '07:00',
        endTime: '15:00',
        teamLeaderId: ' leader-1 ',
        workerIds: ['worker-1'],
        equipmentIds: ['bin-1', 'tractor-1'],
        millId: ' mill-1 ',
        requestIds: ['request-1'],
      }),
    ).toEqual({
      date: '2026-11-25',
      startTime: '07:00',
      endTime: '15:00',
      teamLeaderId: 'leader-1',
      workerIds: ['worker-1'],
      equipmentIds: ['bin-1', 'tractor-1'],
      millId: 'mill-1',
      requestIds: ['request-1'],
    })
  })
})

describe('planning warnings and conflicts', () => {
  it('renders the low mill capacity warning with the code visible', () => {
    const warning: RoundWarning = { code: 'MILL_CAPACITY_LOW', message: 'Mill capacity is lower than the estimate.' }
    expect(formatRoundWarning(warning)).toMatch(/MILL_CAPACITY_LOW/)
    expect(formatRoundWarning(warning)).toMatch(/capacité/)
  })

  it('renders a useful booking conflict message', () => {
    expect(
      planningErrorMessage(new ApiError(409, 'BOOKING_CONFLICT', 'Bin B-07 is already booked by round T-2026-118.')),
    ).toBe('Conflit de réservation : Bin B-07 is already booked by round T-2026-118.')
  })
})

describe('planning DTO normalization', () => {
  it('normalizes the backend suggestion resource fields and group request ids', () => {
    const suggestions = normalizePlanningSuggestions({
      availableWorkers: [{ id: 'w-1', resourceKind: 'WORKER', firstName: 'Sami', lastName: 'Trabelsi', available: true }],
      availableEquipment: [{ id: 'b-1', resourceKind: 'EQUIPMENT', resourceType: 'BIN', code: 'B-07', available: true }],
      groups: [{ requests: [{ id: 'request-1' } as HarvestRequest], estimatedTotalKg: 900, nearestMillId: 'mill-1' }],
    })
    expect(suggestions.availability.workers[0]).toMatchObject({ id: 'w-1', available: true })
    expect(suggestions.availability.equipment[0]).toMatchObject({ id: 'b-1', resourceType: 'BIN' })
    expect(suggestions.groups[0].requestIds).toEqual(['request-1'])
  })

  it('normalizes grouped free and busy resources for the form', () => {
    const availability = normalizeAvailability({
      workers: [{ id: 'w-1', resourceKind: 'WORKER', firstName: 'Sami', lastName: 'Trabelsi', available: true }],
      equipment: [
        { id: 'b-1', resourceKind: 'EQUIPMENT', code: 'B-07', resourceType: 'BIN', capacityKg: 4000, available: true },
        { id: 't-1', resourceKind: 'EQUIPMENT', code: 'TR-02', resourceType: 'TRACTOR', reason: 'Booked', available: false },
      ],
    })
    expect(availability.workers[0]).toMatchObject({ id: 'w-1', resourceKind: 'WORKER', available: true })
    expect(availability.equipment).toEqual([
      expect.objectContaining({ id: 'b-1', resourceKind: 'EQUIPMENT', resourceType: 'BIN', available: true }),
      expect.objectContaining({ id: 't-1', resourceKind: 'EQUIPMENT', resourceType: 'TRACTOR', available: false }),
    ])
  })

  it('normalizes list and mutation response envelopes', () => {
    const round = { id: 'r-1', code: 'T-2026-118' } as unknown as CollectionRound
    expect(normalizeRoundPage([round]).content).toEqual([round])
    expect(normalizeRoundMutation({ round, warnings: ['MILL_CAPACITY_LOW'] }).warnings).toEqual([
      { code: 'MILL_CAPACITY_LOW', message: expect.any(String) },
    ])
  })
})

describe('stale planning selections', () => {
  it('blocks a request or resource that disappeared from the latest availability result', () => {
    const errors = validateRoundSelection(
      {
        date: '2026-11-25',
        startTime: '07:00',
        endTime: '15:00',
        teamLeaderId: 'leader-1',
        workerIds: ['worker-gone'],
        equipmentIds: ['bin-gone'],
        millId: 'mill-1',
        requestIds: ['request-gone'],
      },
      {
        requests: [],
        teamLeaders: [{ id: 'leader-1' }],
        availability: { workers: [], equipment: [] },
        mills: [{ id: 'mill-1', name: 'Mill', availableCapacityKg: 10_000 }],
      },
    )
    expect(errors).toEqual([
      'La demande request-gone n’est plus disponible.',
      'La ressource worker-gone n’est plus disponible.',
      'La ressource bin-gone n’est plus disponible.',
    ])
  })
})
