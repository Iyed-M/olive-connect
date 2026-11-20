import { api } from '@/lib/api'

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

// The approve response is the request plus the temporary password, shown once.
export interface ApproveResponse extends Registration {
  temporaryPassword: string
}

export const createRegistration = (input: RegistrationInput) =>
  api<void>('/registrations', { method: 'POST', body: JSON.stringify(input) })

export const listRegistrations = (status: RegistrationStatus) =>
  api<Registration[]>(`/registrations?status=${status}`)

export const approveRegistration = (id: string) =>
  api<ApproveResponse>(`/registrations/${id}/approve`, { method: 'POST' })

export const rejectRegistration = (id: string) =>
  api<Registration>(`/registrations/${id}/reject`, { method: 'POST' })

export const STATUS_LABEL: Record<RegistrationStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}
