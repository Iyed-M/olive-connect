import { createFileRoute } from '@tanstack/react-router'
import { PlanningBoardPage } from '@/features/collection/PlanningBoard'

export const Route = createFileRoute('/_app/collection/planning')({ component: PlanningBoardPage })
