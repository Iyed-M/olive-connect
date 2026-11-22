import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Me } from './types'
import { rememberOnboardingDone, restoreOnboardingState } from './onboarding'

const user: Me = {
  id: 'user-1',
  email: 'farmer@example.test',
  firstName: 'Amina',
  lastName: 'Farmer',
  roles: ['FARMER'],
  onboardingDone: false,
}

describe('onboarding persistence fallback', () => {
  const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  let values: Map<string, string>

  beforeEach(() => {
    values = new Map()
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
      },
    })
  })

  afterEach(() => {
    if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
    else delete (globalThis as { localStorage?: Storage }).localStorage
  })

  it('does not override a server-completed onboarding state', () => {
    const completed = { ...user, onboardingDone: true }
    expect(restoreOnboardingState(completed)).toBe(completed)
  })

  it('restores only the User who completed the local fallback', () => {
    rememberOnboardingDone(user.id)

    expect(restoreOnboardingState(user)).toMatchObject({ onboardingDone: true })
    expect(restoreOnboardingState({ ...user, id: 'other-user' })).toMatchObject({ onboardingDone: false })
  })
})
