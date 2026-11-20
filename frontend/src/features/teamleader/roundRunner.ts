import { ApiError } from '@/lib/api'
import {
  completeRound,
  startRound,
  updateRoundStop,
  type CollectionRound,
  type RoundStatus,
  type RoundStopUpdateBody,
  type RoundStopUpdateStatus,
  type StopStatus,
} from '@/features/collection/planning'

export interface StopFormValues {
  harvestedKg: string
  crateCount: string
  skipReason: string
}

export interface StopDraft {
  status: StopStatus
  harvestedKg: string
  crateCount: string
  comment: string
}

export interface RoundDraft {
  roundId: string
  status: RoundStatus
  stops: Record<string, StopDraft>
  updatedAt: number
}

export type RoundMutation =
  | { id: string; roundId: string; action: 'START'; createdAt: number }
  | { id: string; roundId: string; action: 'STOP'; order: number; body: RoundStopUpdateBody; createdAt: number }
  | { id: string; roundId: string; action: 'COMPLETE'; createdAt: number }

type RoundMutationInput =
  | { roundId: string; action: 'START' }
  | { roundId: string; action: 'STOP'; order: number; body: RoundStopUpdateBody }
  | { roundId: string; action: 'COMPLETE' }

export interface RoundRunnerStore {
  getDraft(roundId: string): Promise<RoundDraft | null>
  saveDraft(draft: RoundDraft): Promise<void>
  deleteDraft(roundId: string): Promise<void>
  listMutations(roundId?: string): Promise<RoundMutation[]>
  enqueueMutation(mutation: RoundMutation): Promise<void>
  removeMutation(id: string): Promise<void>
}

export interface RetryResult {
  synced: number
  pending: number
  responses: Array<{ mutation: RoundMutation; round: CollectionRound }>
  error: unknown | null
}

const DATABASE_NAME = 'oliveconnect-round-runner'
const DATABASE_VERSION = 1
const DRAFT_STORE = 'drafts'
const MUTATION_STORE = 'mutations'

export function buildStopUpdate(
  status: Extract<RoundStopUpdateStatus, 'DONE' | 'SKIPPED'>,
  values: StopFormValues,
): { body: RoundStopUpdateBody | null; errors: string[] } {
  if (status === 'SKIPPED') {
    const reason = values.skipReason.trim()
    return reason
      ? { body: { status, harvestedKg: null, crateCount: null, comment: reason }, errors: [] }
      : { body: null, errors: ['Indiquez pourquoi cet arrêt est ignoré.'] }
  }

  const errors: string[] = []
  const harvestedKg = Number(values.harvestedKg)
  const crateCount = Number(values.crateCount)
  if (!Number.isFinite(harvestedKg) || harvestedKg <= 0) {
    errors.push('La quantité récoltée doit être supérieure à zéro.')
  }
  if (!Number.isInteger(crateCount) || crateCount <= 0) {
    errors.push('Le nombre de caisses doit être un entier supérieur à zéro.')
  }
  return errors.length > 0
    ? { body: null, errors }
    : { body: { status, harvestedKg, crateCount, comment: null }, errors: [] }
}

export function createRoundDraft(round: CollectionRound): RoundDraft {
  const stops: Record<string, StopDraft> = {}
  for (const stop of round.stops) {
    stops[String(stop.order)] = stopDraft(stop.status, stop.harvestedKg, stop.crateCount, stop.comment)
  }
  return { roundId: round.id, status: round.status, stops, updatedAt: Date.now() }
}

export function mergeRoundWithDraft(round: CollectionRound, draft: RoundDraft): CollectionRound {
  return {
    ...round,
    status: draft.status,
    stops: round.stops.map((stop) => {
      const saved = draft.stops[String(stop.order)]
      if (!saved) return stop
      return {
        ...stop,
        status: saved.status,
        harvestedKg: numberOrNull(saved.harvestedKg, stop.harvestedKg),
        crateCount: integerOrNull(saved.crateCount, stop.crateCount),
        comment: saved.comment || null,
      }
    }),
  }
}

export function draftForServerRound(
  current: RoundDraft,
  round: CollectionRound,
  mutation: RoundMutation,
): RoundDraft {
  const next = createRoundDraft(round)
  for (const [order, stop] of Object.entries(current.stops)) {
    if (mutation.action === 'STOP' && String(mutation.order) === order) continue
    next.stops[order] = stop
  }
  return next
}

export function createRoundMutation(
  input: RoundMutationInput,
): RoundMutation {
  return { ...input, id: mutationId(), createdAt: Date.now() } as RoundMutation
}

export function isRetryableMutationError(error: unknown): boolean {
  if (!(error instanceof ApiError)) return true
  return error.status === 0 || error.status >= 500
}

export async function retryQueuedMutations(
  store: RoundRunnerStore,
  send: (mutation: RoundMutation) => Promise<CollectionRound>,
  roundId?: string,
): Promise<RetryResult> {
  const queued = await store.listMutations(roundId)
  const responses: RetryResult['responses'] = []
  let error: unknown | null = null

  for (const mutation of queued) {
    try {
      const round = await send(mutation)
      await store.removeMutation(mutation.id)
      responses.push({ mutation, round })
    } catch (caught) {
      error = caught
      break
    }
  }

  return {
    synced: responses.length,
    pending: (await store.listMutations(roundId)).length,
    responses,
    error,
  }
}

export function createRoundRunnerStore(): RoundRunnerStore {
  return new IndexedDbRoundRunnerStore()
}

export function createMemoryRoundRunnerStore(): RoundRunnerStore {
  const drafts = new Map<string, RoundDraft>()
  const mutations = new Map<string, RoundMutation>()
  return {
    async getDraft(roundId) {
      const draft = drafts.get(roundId)
      return draft ? clone(draft) : null
    },
    async saveDraft(draft) {
      drafts.set(draft.roundId, clone(draft))
    },
    async deleteDraft(roundId) {
      drafts.delete(roundId)
    },
    async listMutations(roundId) {
      return [...mutations.values()]
        .filter((mutation) => !roundId || mutation.roundId === roundId)
        .sort((left, right) => left.createdAt - right.createdAt)
        .map(clone)
    },
    async enqueueMutation(mutation) {
      mutations.set(mutation.id, clone(mutation))
    },
    async removeMutation(id) {
      mutations.delete(id)
    },
  }
}

class IndexedDbRoundRunnerStore implements RoundRunnerStore {
  private readonly database = openDatabase()

  getDraft(roundId: string): Promise<RoundDraft | null> {
    return this.request<RoundDraft | undefined>(DRAFT_STORE, 'readonly', (store) => store.get(roundId))
      .then((draft) => draft ?? null)
  }

  saveDraft(draft: RoundDraft): Promise<void> {
    return this.request(DRAFT_STORE, 'readwrite', (store) => store.put(draft)).then(() => undefined)
  }

  deleteDraft(roundId: string): Promise<void> {
    return this.request(DRAFT_STORE, 'readwrite', (store) => store.delete(roundId)).then(() => undefined)
  }

  listMutations(roundId?: string): Promise<RoundMutation[]> {
    return this.request<RoundMutation[]>(MUTATION_STORE, 'readonly', (store) => store.getAll()).then((mutations) =>
      mutations
        .filter((mutation) => !roundId || mutation.roundId === roundId)
        .sort((left, right) => left.createdAt - right.createdAt),
    )
  }

  enqueueMutation(mutation: RoundMutation): Promise<void> {
    return this.request(MUTATION_STORE, 'readwrite', (store) => store.put(mutation)).then(() => undefined)
  }

  removeMutation(id: string): Promise<void> {
    return this.request(MUTATION_STORE, 'readwrite', (store) => store.delete(id)).then(() => undefined)
  }

  private async request<T>(
    storeName: string,
    mode: IDBTransactionMode,
    operation: (store: IDBObjectStore) => IDBRequest,
  ): Promise<T> {
    const database = await this.database
    return new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(storeName, mode)
      const request = operation(transaction.objectStore(storeName))
      request.onsuccess = () => resolve(request.result as T)
      request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'))
      transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'))
    })
  }
}

function openDatabase(): Promise<IDBDatabase> {
  if (typeof indexedDB === 'undefined') {
    return Promise.reject(new Error('IndexedDB is not available in this browser'))
  }

  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(DRAFT_STORE)) database.createObjectStore(DRAFT_STORE, { keyPath: 'roundId' })
      if (!database.objectStoreNames.contains(MUTATION_STORE)) database.createObjectStore(MUTATION_STORE, { keyPath: 'id' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB could not be opened'))
  })
}

function stopDraft(status: StopStatus, harvestedKg: number | null, crateCount: number | null, comment?: string | null): StopDraft {
  return {
    status,
    harvestedKg: harvestedKg == null ? '' : String(harvestedKg),
    crateCount: crateCount == null ? '' : String(crateCount),
    comment: comment ?? '',
  }
}

function numberOrNull(value: string, fallback: number | null): number | null {
  if (!value.trim()) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function integerOrNull(value: string, fallback: number | null): number | null {
  if (!value.trim()) return null
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : fallback
}

function mutationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function clone<T>(value: T): T {
  return structuredClone(value)
}

export async function sendRoundMutation(mutation: RoundMutation): Promise<CollectionRound> {
  if (mutation.action === 'START') return startRound(mutation.roundId)
  if (mutation.action === 'COMPLETE') return completeRound(mutation.roundId)
  return updateRoundStop(mutation.roundId, mutation.order, mutation.body)
}
