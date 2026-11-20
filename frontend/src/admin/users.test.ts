import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { errorBanner, fieldMessages, setServerErrors } from './users'

describe('fieldMessages', () => {
  it('maps field errors by name and keeps the first message', () => {
    expect(
      fieldMessages([
        { field: 'email', message: 'a' },
        { field: 'email', message: 'b' },
      ]),
    ).toEqual({ email: 'a' })
  })
  it('strips the farmerProfile prefix', () => {
    expect(fieldMessages([{ field: 'farmerProfile.cin', message: 'bad' }])).toEqual({ cin: 'bad' })
  })
})

describe('setServerErrors', () => {
  it('sets one server error per field, without the farmerProfile prefix', () => {
    const setError = vi.fn()
    setServerErrors(
      [
        { field: 'farmerProfile.cin', message: 'bad cin' },
        { field: 'email', message: 'bad email' },
      ],
      setError,
    )
    expect(setError).toHaveBeenCalledWith('cin', { type: 'server', message: 'bad cin' })
    expect(setError).toHaveBeenCalledWith('email', { type: 'server', message: 'bad email' })
  })
})

describe('errorBanner', () => {
  it('has no banner when field errors are shown', () => {
    const e = new ApiError(400, 'VALIDATION_FAILED', 'Invalid', [{ field: 'cin', message: 'x' }])
    expect(errorBanner(e)).toBeNull()
  })
  it('explains SELF_LOCKOUT and conflicts', () => {
    expect(errorBanner(new ApiError(422, 'SELF_LOCKOUT', 'x'))).toMatch(/yourself/)
    expect(errorBanner(new ApiError(409, 'EMAIL_TAKEN', 'x'))).toMatch(/email/)
  })
  it('falls back to the server detail', () => {
    expect(errorBanner(new ApiError(500, 'UNKNOWN', 'Boom'))).toBe('Boom')
  })
})
