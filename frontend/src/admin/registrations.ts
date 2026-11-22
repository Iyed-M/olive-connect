import { api } from '@/lib/api'
import type { Delegation } from '@/admin/users'

// Same values as the Java RegistrationStatus enum.
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

// Mirrors the backend RegistrationRequest DTO (docs/06 registrations).
export interface Registration {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  cin: string | null
  message: string | null
  status: RegistrationStatus
  createdAt: string
  processedById: string | null
}

// Body of the public form (POST /registrations).
export interface RegistrationInput {
  firstName: string
  lastName: string
  email: string
  phone: string
  cin: string
  message: string
}

// Body of approve: the request has no member number or delegation, the admin supplies them.
export interface ApproveInput {
  memberNumber: string
  delegation: Delegation
}

// The approve response carries the temporary password, shown once.
export interface ApproveResponse {
  registration: Registration
  userId: string
  temporaryPassword: string
}

export const createRegistration = (input: RegistrationInput) =>
  api<void>('/registrations', { method: 'POST', body: JSON.stringify(input) })

export const listRegistrations = (status: RegistrationStatus) =>
  api<Registration[]>(`/registrations?status=${status}`)

export const approveRegistration = (id: string, input: ApproveInput) =>
  api<ApproveResponse>(`/registrations/${id}/approve`, { method: 'POST', body: JSON.stringify(input) })

export const rejectRegistration = (id: string) =>
  api<Registration>(`/registrations/${id}/reject`, { method: 'POST' })

export const STATUS_LABEL: Record<RegistrationStatus, string> = {
  PENDING: 'En attente',
  APPROVED: 'Approuvée',
  REJECTED: 'Refusée',
}
