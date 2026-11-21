import { CalendarDays, ClipboardList, Factory, FileUp, Gauge, HardHat, Map, Route, Scale, Settings, Sprout, Truck, UserPlus, Users, type LucideIcon } from 'lucide-react'
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
  FARMER: [
    { to: '/farmer', label: 'Accueil', icon: Sprout },
    { to: '/farmer/requests', label: 'Demandes', icon: ClipboardList },
    { to: '/farmer/orchards', label: 'Vergers', icon: Map },
    { to: '/farmer/receptions', label: 'Réceptions', icon: Scale },
  ],
  COLLECTION_MANAGER: [
    { to: '/collection/requests', label: 'Demandes', icon: ClipboardList },
    { to: '/collection/planning', label: 'Planification', icon: CalendarDays },
  ],
  TEAM_LEADER: [{ to: '/rounds/mine', label: 'Mes tournées', icon: Route }],
  LOGISTICS_MANAGER: [
    { to: '/logistics/workers', label: 'Ouvriers', icon: HardHat },
    { to: '/logistics/equipment', label: 'Équipements', icon: Truck },
  ],
  MILL_MANAGER: [
    { to: '/mill/incoming', label: 'Réceptions', icon: Factory },
    { to: '/mill/capacity', label: 'Capacité', icon: Gauge },
  ],
  // The Admin has full access to Workers and Equipment too (same pages as the Logistics manager).
  ADMIN: [
    { to: '/admin/users', label: 'Utilisateurs', icon: Users },
    { to: '/admin/registrations', label: 'Inscriptions', icon: UserPlus },
    { to: '/logistics/workers', label: 'Ouvriers', icon: HardHat },
    { to: '/logistics/equipment', label: 'Équipements', icon: Truck },
    { to: '/intranet/imports', label: 'Imports', icon: FileUp },
    { to: '/intranet/settings', label: 'Paramètres', icon: Settings },
  ],
}
