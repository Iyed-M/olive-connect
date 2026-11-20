import type { Me } from './types'

// TEMPORARY: the real role-priority and Role home mapping comes with the layout ticket.
export const homeFor = (user: Me) => (user.roles.includes('ADMIN') ? '/admin/users' : '/')
