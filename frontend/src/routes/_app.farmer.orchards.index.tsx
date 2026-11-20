import { createFileRoute } from '@tanstack/react-router'
import { OrchardsPage } from '@/features/farmer/OrchardsPage'

export const Route = createFileRoute('/_app/farmer/orchards/')({ component: OrchardsPage })
