import { createFileRoute } from '@tanstack/react-router'
import { AlertsBoardPage } from '@/features/collection/AlertsBoard'

export const Route = createFileRoute('/_app/collection/alerts')({ component: AlertsBoardPage })
