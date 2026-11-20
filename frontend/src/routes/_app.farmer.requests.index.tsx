import { createFileRoute } from '@tanstack/react-router'
import { MyRequestsPage } from '@/features/farmer/MyRequests'

export const Route = createFileRoute('/_app/farmer/requests/')({ component: MyRequestsPage })
