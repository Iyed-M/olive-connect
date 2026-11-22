import { describe, expect, it } from 'vitest'
import { ROLE_LABEL } from '@/auth/roles'
import type { Role } from '@/auth/types'
import { shouldStartTour, tourStepsForRole } from './index'

const roles: Role[] = ['FARMER', 'COLLECTION_MANAGER', 'TEAM_LEADER', 'LOGISTICS_MANAGER', 'MILL_MANAGER', 'ADMIN']

describe('role guided tours', () => {
  it('defines a usable first-login tour for every role', () => {
    for (const role of roles) {
      const steps = tourStepsForRole(role)
      expect(steps.length).toBeGreaterThanOrEqual(3)
      expect(steps.every((step) => step.target.startsWith('[data-tour='))).toBe(true)
      expect(steps.some((step) => step.content.includes(ROLE_LABEL[role]))).toBe(true)
    }
  })

  it('starts only while the server says onboarding is incomplete', () => {
    expect(shouldStartTour(false)).toBe(true)
    expect(shouldStartTour(true)).toBe(false)
  })
})
