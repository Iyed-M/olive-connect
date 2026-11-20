import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import type { CollectionRound, RoundStopUpdateBody } from '@/features/collection/planning'
import {
  buildStopUpdate,
  createMemoryRoundRunnerStore,
  createRoundDraft,
  isRetryableMutationError,
  roundFromDraft,
  retryQueuedMutations,
  type RoundMutation,
} from './roundRunner'

const round = {
  id: 'round-1',
  code: 'T-2026-118',
  date: '2026-11-25',
  startTime: '07:00',
  endTime: '15:00',
  status: 'IN_PROGRESS',
  teamLeaderId: 'leader-1',
  millId: 'mill-1',
  workerIds: [],
  equipmentIds: [],
  estimatedTotalKg: 3200,
  totalHarvestedKg: 0,
  version: 2,
  stops: [
    {
      order: 1,
      harvestRequestId: 'request-1',
      orchardId: 'orchard-1',
      orchardName: 'Henchir El Mezraa',
      plannedArrival: '07:00',
      estimatedKg: 3200,
      status: 'IN_PROGRESS',
      harvestedKg: null,
      crateCount: null,
      comment: null,
    },
  ],
} as CollectionRound

describe('round stop payloads', () => {
  it('builds a DONE payload from the mobile form values', () => {
    expect(buildStopUpdate('DONE', { harvestedKg: '3200', crateCount: '80', skipReason: '' })).toEqual({
      body: { status: 'DONE', harvestedKg: 3200, crateCount: 80, comment: null },
      errors: [],
    })
  })

  it('builds a SKIPPED payload only when a reason is supplied', () => {
    expect(buildStopUpdate('SKIPPED', { harvestedKg: '', crateCount: '', skipReason: 'Route inaccessible' })).toEqual({
      body: { status: 'SKIPPED', harvestedKg: null, crateCount: null, comment: 'Route inaccessible' },
      errors: [],
    })
    expect(buildStopUpdate('SKIPPED', { harvestedKg: '', crateCount: '', skipReason: '  ' })).toEqual({
      body: null,
      errors: ['Indiquez pourquoi cet arrêt est ignoré.'],
    })
  })

  it('rejects invalid harvested quantities before a mutation is queued', () => {
    expect(buildStopUpdate('DONE', { harvestedKg: '-1', crateCount: '1', skipReason: '' })).toEqual({
      body: null,
      errors: ['La quantité récoltée doit être supérieure à zéro.'],
    })
  })
})

describe('round runner persistence', () => {
  it('keeps drafts and queued mutations in the same durable store', async () => {
    const store = createMemoryRoundRunnerStore()
    const draft = createRoundDraft(round)
    draft.stops['1'].harvestedKg = '3200'
    const mutation: RoundMutation = {
      id: 'mutation-1',
      roundId: round.id,
      action: 'STOP',
      order: 1,
      body: { status: 'DONE', harvestedKg: 3200, crateCount: 80, comment: null },
      createdAt: 1,
    }

    await store.saveDraft(draft)
    await store.enqueueMutation(mutation)

    await expect(store.getDraft(round.id)).resolves.toMatchObject({ stops: { '1': { harvestedKg: '3200' } } })
    await expect(store.listMutations(round.id)).resolves.toEqual([mutation])
  })

  it('keeps a complete round snapshot that can reopen while the API is offline', () => {
    const draft = createRoundDraft(round)
    draft.status = 'IN_PROGRESS'
    draft.stops['1'].status = 'DONE'
    draft.stops['1'].harvestedKg = '3200'

    expect(roundFromDraft(draft)).toMatchObject({
      id: round.id,
      code: round.code,
      status: 'IN_PROGRESS',
      stops: [{ order: 1, status: 'DONE', harvestedKg: 3200 }],
    })
  })
})

describe('round runner retry queue', () => {
  it('preserves order when the first retry is offline and drains after reconnecting', async () => {
    const store = createMemoryRoundRunnerStore()
    const body: RoundStopUpdateBody = { status: 'IN_PROGRESS' }
    const first: RoundMutation = { id: 'first', roundId: round.id, action: 'START', createdAt: 1 }
    const second: RoundMutation = { id: 'second', roundId: round.id, action: 'STOP', order: 1, body, createdAt: 2 }
    await store.enqueueMutation(first)
    await store.enqueueMutation(second)

    let attempts = 0
    const offline = await retryQueuedMutations(store, async () => {
      attempts += 1
      if (attempts === 1) throw new Error('offline')
      return round
    }, round.id)
    expect(offline).toMatchObject({ synced: 0, pending: 2 })
    await expect(store.listMutations(round.id)).resolves.toHaveLength(2)

    const syncedIds: string[] = []
    const online = await retryQueuedMutations(store, async (mutation) => {
      syncedIds.push(mutation.id)
      attempts += 1
      return round
    }, round.id)
    expect(online).toMatchObject({ synced: 2, pending: 0 })
    expect(syncedIds).toEqual(['first', 'second'])
    await expect(store.listMutations(round.id)).resolves.toEqual([])
  })

  it('queues network and server availability failures, but not validation errors', () => {
    expect(isRetryableMutationError(new TypeError('Failed to fetch'))).toBe(true)
    expect(isRetryableMutationError(new ApiError(503, 'INTERNAL_ERROR', 'Unavailable'))).toBe(true)
    expect(isRetryableMutationError(new ApiError(422, 'VALIDATION_FAILED', 'Invalid stop'))).toBe(false)
  })

  it('removes a permanent failure and continues with later queued mutations', async () => {
    const store = createMemoryRoundRunnerStore()
    const invalid: RoundMutation = { id: 'invalid', roundId: round.id, action: 'START', createdAt: 1 }
    const later: RoundMutation = { id: 'later', roundId: round.id, action: 'COMPLETE', createdAt: 2 }
    await store.enqueueMutation(invalid)
    await store.enqueueMutation(later)

    const sent: string[] = []
    const result = await retryQueuedMutations(store, async (mutation) => {
      sent.push(mutation.id)
      if (mutation.id === invalid.id) throw new ApiError(422, 'ROUND_NOT_PLANNED', 'Already started')
      return round
    }, round.id)

    expect(result.synced).toBe(1)
    expect(result.pending).toBe(0)
    expect(result.error).toBeInstanceOf(ApiError)
    expect(sent).toEqual(['invalid', 'later'])
    await expect(store.listMutations(round.id)).resolves.toEqual([])
  })
})
