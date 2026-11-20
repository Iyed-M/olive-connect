import { ClipboardList, Factory, Route, Sprout, Truck, Users, type LucideIcon } from 'lucide-react'
import type { LinkProps } from '@tanstack/react-router'
import type { Role } from './types'

export interface NavItem {
  to: LinkProps['to']
  label: string
  icon: LucideIcon
}

// Navigation entries of each Active role. In M1a each role only has its home;
// later milestones add entries here.
export const NAV: Record<Role, NavItem[]> = {
  FARMER: [{ to: '/farmer', label: 'Home', icon: Sprout }],
  COLLECTION_MANAGER: [{ to: '/collection/requests', label: 'Requests', icon: ClipboardList }],
  TEAM_LEADER: [{ to: '/rounds/mine', label: 'My rounds', icon: Route }],
  LOGISTICS_MANAGER: [{ to: '/logistics/workers', label: 'Workers', icon: Truck }],
  MILL_MANAGER: [{ to: '/mill/incoming', label: 'Incoming', icon: Factory }],
  ADMIN: [{ to: '/admin/users', label: 'Users', icon: Users }],
}
