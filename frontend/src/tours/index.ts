import type { Role } from '@/auth/types'

export interface TourStep {
  target: string
  title: string
  content: string
}

const roleTours: Record<Role, readonly TourStep[]> = {
  FARMER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your Farmer navigation',
      content: 'As a FARMER, use this navigation to reach your orchards, harvest requests and field alerts.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'This area changes with the page you choose and keeps the actions for your FARMER role close at hand.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help whenever you need a short explanation of the current FARMER page.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the FARMER tour whenever the workflow changes.',
    },
  ],
  COLLECTION_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your collection navigation',
      content: 'As a COLLECTION_MANAGER, use this navigation for requests, planning, alerts and cooperative operations.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'The selected collection page and its queue or planning controls appear here for the COLLECTION_MANAGER role.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help to see the guidance for the current COLLECTION_MANAGER page.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the COLLECTION_MANAGER tour whenever you need it.',
    },
  ],
  TEAM_LEADER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your field navigation',
      content: 'As a TEAM_LEADER, use this navigation to open your assigned rounds and field runner.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'The selected round and its stops appear here for the TEAM_LEADER role.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help for concise instructions while working as a TEAM_LEADER in the field.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the TEAM_LEADER tour at any time.',
    },
  ],
  LOGISTICS_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your logistics navigation',
      content: 'As a LOGISTICS_MANAGER, use this navigation for workers, equipment, availability and supplies.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'The selected resource list or calendar appears here for the LOGISTICS_MANAGER role.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help to get guidance for the current LOGISTICS_MANAGER page.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the LOGISTICS_MANAGER tour whenever you need it.',
    },
  ],
  MILL_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your mill navigation',
      content: 'As a MILL_MANAGER, use this navigation for incoming rounds, receptions, capacity and dashboard views.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'The selected mill queue or reception form appears here for the MILL_MANAGER role.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help for concise guidance on the current MILL_MANAGER page.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the MILL_MANAGER tour at any time.',
    },
  ],
  ADMIN: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Your administration navigation',
      content: 'As an ADMIN, use this navigation for users, resources, imports, settings and cooperative dashboards.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Your work area',
      content: 'The selected administration page appears here for the ADMIN role.',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Contextual help',
      content: 'Open help to see the guidance for the current ADMIN page.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Replay this tour',
      content: 'Open the profile menu to replay the ADMIN tour whenever you need it.',
    },
  ],
}

export function tourStepsForRole(role: Role): readonly TourStep[] {
  return roleTours[role]
}

export function shouldStartTour(onboardingDone: boolean): boolean {
  return !onboardingDone
}
