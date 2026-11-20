// Same values as the Java Role enum (tn.oliveconnect.user.Role).
export type Role =
  | 'FARMER'
  | 'COLLECTION_MANAGER'
  | 'TEAM_LEADER'
  | 'LOGISTICS_MANAGER'
  | 'MILL_MANAGER'
  | 'ADMIN'

// Mirrors the backend MeDto.
export interface Me {
  id: string
  email: string
  firstName: string
  lastName: string
  roles: Role[]
  onboardingDone: boolean
}

// Mirrors the backend LoginResponse.
export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: Me
}
