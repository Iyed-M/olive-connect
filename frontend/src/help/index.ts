import type { Role } from '@/auth/types'
import adminMarkdown from './admin.md?raw'
import collectionManagerMarkdown from './collection-manager.md?raw'
import farmerMarkdown from './farmer.md?raw'
import farmerOrchardsMarkdown from './farmer-orchards.md?raw'
import farmerRequestsMarkdown from './farmer-requests.md?raw'
import globalMarkdown from './global.md?raw'
import logisticsManagerMarkdown from './logistics-manager.md?raw'
import millManagerMarkdown from './mill-manager.md?raw'
import notificationsMarkdown from './notifications.md?raw'
import teamLeaderMarkdown from './team-leader.md?raw'

export interface HelpDocument {
  key: string
  title: string
  markdown: string
}

export type HelpBlock =
  | { type: 'heading'; level: number; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; items: string[] }

const documents: Record<string, HelpDocument> = {
  admin: { key: 'admin', title: 'Administration and operations', markdown: adminMarkdown },
  'collection-manager': {
    key: 'collection-manager',
    title: 'Collection management',
    markdown: collectionManagerMarkdown,
  },
  farmer: { key: 'farmer', title: 'Farmer home', markdown: farmerMarkdown },
  'farmer-orchards': { key: 'farmer-orchards', title: 'Orchards', markdown: farmerOrchardsMarkdown },
  'farmer-requests': { key: 'farmer-requests', title: 'Harvest requests', markdown: farmerRequestsMarkdown },
  global: { key: 'global', title: 'OliveConnect help', markdown: globalMarkdown },
  'logistics-manager': {
    key: 'logistics-manager',
    title: 'Logistics',
    markdown: logisticsManagerMarkdown,
  },
  'mill-manager': { key: 'mill-manager', title: 'Mill operations', markdown: millManagerMarkdown },
  notifications: { key: 'notifications', title: 'Notifications', markdown: notificationsMarkdown },
  'team-leader': { key: 'team-leader', title: 'Team leader field work', markdown: teamLeaderMarkdown },
}

const routeDocuments: Array<{ prefix: string; key: string }> = [
  { prefix: '/farmer/requests', key: 'farmer-requests' },
  { prefix: '/farmer/orchards', key: 'farmer-orchards' },
  { prefix: '/notifications', key: 'notifications' },
  { prefix: '/farmer', key: 'farmer' },
  { prefix: '/collection', key: 'collection-manager' },
  { prefix: '/rounds', key: 'team-leader' },
  { prefix: '/logistics', key: 'logistics-manager' },
  { prefix: '/mill', key: 'mill-manager' },
  { prefix: '/admin', key: 'admin' },
  { prefix: '/intranet', key: 'admin' },
]

function normalizePath(pathname: string): string {
  const path = pathname.split('?')[0].replace(/\/+$/, '')
  return path || '/'
}

function roleDocumentKey(role: Role): string {
  return role.toLowerCase().replaceAll('_', '-')
}

function matchesRoute(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`)
}

export function helpKeyForPath(pathname: string, role: Role): string {
  const path = normalizePath(pathname)
  const routeMatch = routeDocuments.find(({ prefix }) => matchesRoute(path, prefix))
  if (routeMatch) return routeMatch.key

  const roleKey = roleDocumentKey(role)
  return documents[roleKey] ? roleKey : 'global'
}

export function getHelpDocument(pathname: string, role: Role): HelpDocument {
  const key = helpKeyForPath(pathname, role)
  return documents[key] ?? documents.global
}

export function parseHelpMarkdown(markdown: string): HelpBlock[] {
  const blocks: HelpBlock[] = []
  let paragraph: string[] = []
  let items: string[] = []

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', text: paragraph.join(' ') })
      paragraph = []
    }
  }

  const flushList = () => {
    if (items.length > 0) {
      blocks.push({ type: 'list', items })
      items = []
    }
  }

  for (const line of markdown.split(/\r?\n/)) {
    const heading = /^(#{1,6})\s+(.+)$/.exec(line.trim())
    if (heading) {
      flushParagraph()
      flushList()
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() })
      continue
    }

    const listItem = /^[-*]\s+(.+)$/.exec(line.trim())
    if (listItem) {
      flushParagraph()
      items.push(listItem[1].trim())
      continue
    }

    if (!line.trim()) {
      flushParagraph()
      flushList()
      continue
    }

    flushList()
    paragraph.push(line.trim())
  }

  flushParagraph()
  flushList()
  return blocks
}
