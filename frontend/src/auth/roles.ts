import type { Role } from './types'

export const ROLE_LABEL: Record<Role, string> = {
  FARMER: 'Farmer',
  COLLECTION_MANAGER: 'Collection manager',
  TEAM_LEADER: 'Team leader',
  LOGISTICS_MANAGER: 'Logistics manager',
  MILL_MANAGER: 'Mill manager',
  ADMIN: 'Admin',
}

// Highest priority first (decision 24).
export const ROLE_PRIORITY: Role[] = [
  'ADMIN',
  'COLLECTION_MANAGER',
  'MILL_MANAGER',
  'LOGISTICS_MANAGER',
  'TEAM_LEADER',
  'FARMER',
]

// The Role home of each role. `as const` keeps the exact paths, so the router can type-check them.
export const ROLE_HOME = {
  FARMER: '/farmer',
  COLLECTION_MANAGER: '/collection/requests',
  TEAM_LEADER: '/rounds/mine',
  LOGISTICS_MANAGER: '/logistics/workers',
  MILL_MANAGER: '/mill/incoming',
  ADMIN: '/admin/users',
} as const satisfies Record<Role, string>

// The role with the highest priority among the User's roles. A User always has at least one role;
// FARMER is only a safe default.
export function priorityRole(roles: Role[]): Role {
  return ROLE_PRIORITY.find((role) => roles.includes(role)) ?? 'FARMER'
}

// The Active role: the stored choice if the User still holds it, else the priority role.
// `stored` comes from localStorage, so it can be anything.
export function activeRole(roles: Role[], stored: string | null): Role {
  const chosen = roles.find((role) => role === stored)
  return chosen ?? priorityRole(roles)
}
