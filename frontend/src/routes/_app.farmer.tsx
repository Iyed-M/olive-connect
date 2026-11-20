import { Outlet, createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/_app/farmer')({ component: FarmerLayout })

function FarmerLayout() {
  return <Outlet />
}
