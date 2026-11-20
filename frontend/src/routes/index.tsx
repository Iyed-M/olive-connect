import { createFileRoute } from '@tanstack/react-router'
import { PublicMapPage } from '@/features/public/PublicMapPage'

// The public home page: the orchard map, no login needed.
export const Route = createFileRoute('/')({ component: PublicMapPage })
