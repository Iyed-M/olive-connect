import { createFileRoute } from '@tanstack/react-router'
import { MyReceptionsPage } from '@/features/farmer/MyReceptions'

export const Route = createFileRoute('/_app/farmer/receptions')({ component: MyReceptionsPage })
