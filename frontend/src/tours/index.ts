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
      title: 'Votre navigation agriculteur',
      content: 'Avec le rôle « Agriculteur », utilisez cette navigation pour accéder à vos vergers, à vos demandes de récolte et à vos alertes terrain.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'Cette zone change selon la page choisie et garde à portée de main les actions du rôle « Agriculteur ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide dès que vous avez besoin d’une courte explication sur la page « Agriculteur » en cours.',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Agriculteur » quand le fonctionnement change.',
    },
  ],
  COLLECTION_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Votre navigation de collecte',
      content: 'Avec le rôle « Responsable de collecte », utilisez cette navigation pour les demandes, la planification, les alertes et les opérations de la coopérative.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'La page de collecte choisie, avec sa file ou ses commandes de planification, s’affiche ici pour le rôle « Responsable de collecte ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide pour voir les conseils de la page en cours du rôle « Responsable de collecte ».',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Responsable de collecte » quand vous en avez besoin.',
    },
  ],
  TEAM_LEADER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Votre navigation terrain',
      content: 'Avec le rôle « Chef d’équipe », utilisez cette navigation pour ouvrir vos tournées affectées et le carnet de terrain.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'La tournée choisie et ses arrêts s’affichent ici pour le rôle « Chef d’équipe ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide pour des instructions concises pendant votre travail de terrain en tant que « Chef d’équipe ».',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Chef d’équipe » à tout moment.',
    },
  ],
  LOGISTICS_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Votre navigation logistique',
      content: 'Avec le rôle « Responsable logistique », utilisez cette navigation pour les ouvriers, les équipements, les disponibilités et les approvisionnements.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'La liste de ressources ou le calendrier choisi s’affiche ici pour le rôle « Responsable logistique ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide pour obtenir des conseils sur la page en cours du rôle « Responsable logistique ».',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Responsable logistique » quand vous en avez besoin.',
    },
  ],
  MILL_MANAGER: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Votre navigation moulin',
      content: 'Avec le rôle « Responsable de moulin », utilisez cette navigation pour les tournées entrantes, les réceptions, la capacité et les tableaux de bord.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'La file du moulin ou le formulaire de réception choisi s’affiche ici pour le rôle « Responsable de moulin ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide pour des conseils concis sur la page en cours du rôle « Responsable de moulin ».',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Responsable de moulin » à tout moment.',
    },
  ],
  ADMIN: [
    {
      target: '[data-tour="role-navigation"]',
      title: 'Votre navigation d’administration',
      content: 'Avec le rôle « Administrateur », utilisez cette navigation pour les utilisateurs, les ressources, les imports, les paramètres et les tableaux de bord de la coopérative.',
    },
    {
      target: '[data-tour="main-content"]',
      title: 'Votre zone de travail',
      content: 'La page d’administration choisie s’affiche ici pour le rôle « Administrateur ».',
    },
    {
      target: '[data-tour="context-help"]',
      title: 'Aide contextuelle',
      content: 'Ouvrez l’aide pour voir les conseils de la page en cours du rôle « Administrateur ».',
    },
    {
      target: '[data-tour="profile-menu"]',
      title: 'Revoir cette visite',
      content: 'Ouvrez le menu du profil pour revoir la visite du rôle « Administrateur » quand vous en avez besoin.',
    },
  ],
}

export function tourStepsForRole(role: Role): readonly TourStep[] {
  return roleTours[role]
}

export function shouldStartTour(onboardingDone: boolean): boolean {
  return !onboardingDone
}
