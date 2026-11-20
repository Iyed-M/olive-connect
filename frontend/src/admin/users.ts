import { api, type ApiError, type FieldError } from '@/lib/api'
import type { Role } from '@/auth/types'

// Same values as the Java Delegation enum. The display labels live here (decision 17).
export const DELEGATION_LABEL = {
  SFAX_VILLE: 'Sfax Ville',
  SFAX_OUEST: 'Sfax Ouest',
  SFAX_SUD: 'Sfax Sud',
  SAKIET_EZZIT: 'Sakiet Ezzit',
  SAKIET_EDDAIER: 'Sakiet Eddaier',
  THYNA: 'Thyna',
  AGAREB: 'Agareb',
  JEBINIANA: 'Jebiniana',
  EL_AMRA: 'El Amra',
  EL_HENCHA: 'El Hencha',
  MENZEL_CHAKER: 'Menzel Chaker',
  GHRAIBA: 'Ghraiba',
  BIR_ALI_BEN_KHALIFA: 'Bir Ali Ben Khalifa',
  SKHIRA: 'Skhira',
  MAHRES: 'Mahres',
  KERKENNAH: 'Kerkennah',
} as const
export type Delegation = keyof typeof DELEGATION_LABEL

// Mirrors the backend FarmerProfileDto.
export interface FarmerProfile {
  memberNumber: string
  cin: string
  delegation: Delegation | ''
  address: string | null
}

// Mirrors the backend UserDto.
export interface UserDto {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  roles: Role[]
  enabled: boolean
  farmerProfile: FarmerProfile | null
  createdAt: string
  version: number
}

// Body of POST /users (password required) and PUT /users/{id} (password optional).
export interface UserWriteBody {
  email: string
  firstName: string
  lastName: string
  phone: string | null
  roles: Role[]
  password?: string
  farmerProfile: FarmerProfile | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface UserFilters {
  role: Role | ''
  enabled: '' | 'true' | 'false'
  page: number
}

export const PAGE_SIZE = 20

export function listUsers({ role, enabled, page }: UserFilters) {
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
  if (role) params.set('role', role)
  if (enabled) params.set('enabled', enabled)
  return api<PageResponse<UserDto>>(`/users?${params}`)
}

export const getUser = (id: string) => api<UserDto>(`/users/${id}`)

export const createUser = (body: UserWriteBody) =>
  api<UserDto>('/users', { method: 'POST', body: JSON.stringify(body) })

export const updateUser = (id: string, body: UserWriteBody) =>
  api<UserDto>(`/users/${id}`, { method: 'PUT', body: JSON.stringify(body) })

export const setUserEnabled = (id: string, enabled: boolean) =>
  api<void>(`/users/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' })

// Server field errors by field name (first message wins). A field of the Farmer profile may be
// reported as "farmerProfile.cin": the form uses the short name "cin".
export function fieldMessages(errors: FieldError[]): Record<string, string> {
  const out: Record<string, string> = {}
  for (const { field, message } of errors) {
    const name = field.startsWith('farmerProfile.') ? field.slice('farmerProfile.'.length) : field
    out[name] ??= message
  }
  return out
}

// The message shown above the form for errors that are not tied to a field.
// 400 is shown field by field; 409 and 422 are business messages from the server.
export function errorBanner(error: ApiError): string | null {
  if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) return null
  switch (error.code) {
    case 'EMAIL_TAKEN':
      return 'This email is already used.'
    case 'MEMBER_NUMBER_TAKEN':
      return 'This member number is already used.'
    case 'SELF_LOCKOUT':
      return 'You cannot disable yourself or remove your own Admin role.'
    default:
      return error.detail
  }
}
