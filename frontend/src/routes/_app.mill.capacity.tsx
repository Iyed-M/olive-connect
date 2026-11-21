import { createFileRoute } from '@tanstack/react-router'
import { CapacityPage } from '@/features/mill/CapacityPage'

export const Route = createFileRoute('/_app/mill/capacity')({ component: CapacityPage })
