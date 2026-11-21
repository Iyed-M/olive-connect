import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import {
  buildReceptionPayload,
  calculateReceptionPreview,
  findIncomingStop,
  formatReceptionError,
  getBinsForStop,
  normalizeIncomingRounds,
  normalizeReceptions,
  type ReceptionFormValues,
} from './receptions'

const incomingRound = {
  id: 'round-1',
  code: 'T-2026-118',
  date: '2026-11-21',
  startTime: '07:00',
  endTime: '15:00',
  status: 'COMPLETED',
  millId: 'mill-1',
  season: '2026-2027',
  estimatedTotalKg: 5000,
  totalHarvestedKg: 3530,
  bins: [{ id: 'bin-1', code: 'B-07', capacityKg: 4000 }],
  stops: [
    {
      order: 1,
      harvestRequestId: 'request-1',
      orchardId: 'orchard-1',
      farmerId: 'farmer-1',
      orchardName: 'Henchir El Mezraa',
      plannedArrival: '10:00',
      estimatedKg: 3500,
      status: 'DONE',
      harvestedKg: 3530,
      crateCount: 80,
      comment: null,
    },
  ],
}

describe('incoming mill round contracts', () => {
  it('normalizes the incoming payload and keeps bins scoped to the round', () => {
    const rounds = normalizeIncomingRounds([incomingRound])
    const target = findIncomingStop(rounds, 'round-1', 1)

    expect(target?.stop.orchardId).toBe('orchard-1')
    expect(getBinsForStop(target!.round, target!.stop)).toEqual([
      { id: 'bin-1', code: 'B-07', capacityKg: 4000 },
    ])
  })

  it('does not treat generic equipment IDs as bins when bin details are absent', () => {
    const [round] = normalizeIncomingRounds([{ ...incomingRound, bins: undefined, equipmentIds: ['bin-1'] }])

    expect(round.bins).toEqual([])
  })
})

describe('reception weighing contracts', () => {
  it('computes the preview independently from the server payload', () => {
    expect(calculateReceptionPreview('4350', '820', 4000)).toEqual({
      grossWeightKg: 4350,
      tareKg: 820,
      netWeightKg: 3530,
      fillRate: 0.8825,
    })
  })

  it('builds the documented reception body from text-backed form values', () => {
    const values: ReceptionFormValues = {
      binId: ' bin-1 ',
      grossWeightKg: '4350',
      tareKg: '820',
      quality: 'GOOD',
    }

    expect(buildReceptionPayload(values, 'round-1', 'orchard-1')).toEqual({
      body: {
        roundId: 'round-1',
        binId: 'bin-1',
        orchardId: 'orchard-1',
        grossWeightKg: 4350,
        tareKg: 820,
        quality: {
          grade: 'GOOD',
          defectRatePct: 0,
          leafRatePct: 0,
          estimatedOilYieldPct: 0,
        },
      },
      errors: {},
    })
  })

  it('reports invalid net weight before submitting', () => {
    expect(
      buildReceptionPayload(
        { binId: 'bin-1', grossWeightKg: '800', tareKg: '820', quality: 'GOOD' },
        'round-1',
        'orchard-1',
      ),
    ).toEqual({
      body: null,
      errors: { grossWeightKg: 'Le poids brut doit être supérieur à la tare.' },
    })
  })
})
describe('reception API response contracts', () => {
  it('normalizes a paged response and a nested quality grade', () => {
    const page = normalizeReceptions({
      content: [
        {
          id: 'reception-1',
          grossWeightKg: 4350,
          tareKg: 820,
          netWeightKg: 3530,
          binCapacityKg: 4000,
          fillRate: 0.8825,
          quality: { grade: 'GOOD', defectRatePct: 3.1 },
          receivedAt: '2026-11-21T12:30:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    })

    expect(page.content[0]).toMatchObject({ id: 'reception-1', quality: { grade: 'GOOD' } })
  })
})

describe('reception errors', () => {
  it('explains ownership, duplicate and business-rule failures', () => {
    expect(formatReceptionError(new ApiError(403, 'FORBIDDEN', 'Forbidden'))).toMatch(/responsable/)
    expect(formatReceptionError(new ApiError(422, 'BIN_NOT_IN_ROUND', 'Not in round'))).toMatch(/tournée/)
    expect(formatReceptionError(new ApiError(409, 'RECEPTION_EXISTS', 'Already weighed'))).toMatch(/déjà enregistrée/)
    expect(formatReceptionError(new ApiError(422, 'INVALID_WEIGHING', 'Check weighing values'))).toMatch(/poids/)
  })
})
