import { createFileRoute, Link } from '@tanstack/react-router'
import { ClipboardList, Plus, Sprout, TriangleAlert } from 'lucide-react'
import { RequireRole } from '@/components/RequireRole'
import { buttonVariants } from '@/components/ui/button'

export const Route = createFileRoute('/_app/farmer/')({ component: Page })

function Page() {
  return (
    <RequireRole role="FARMER">
      <div className="flex flex-col gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Accueil agriculteur</h1>
          <p className="text-muted-foreground">Gérez vos vergers et vos demandes de collecte.</p>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Link to="/farmer/requests" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <ClipboardList className="size-6 text-primary" aria-hidden />
            <span className="font-medium">Mes demandes de récolte</span>
          </Link>
          <Link to="/farmer/orchards" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <Sprout className="size-6 text-primary" aria-hidden />
            <span className="font-medium">Mes vergers</span>
          </Link>
          <Link to="/farmer/orchards/new" className={buttonVariants({ className: 'min-h-28 flex-col justify-center gap-2' })}>
            <Plus aria-hidden /> Ajouter un verger
          </Link>
          <Link to="/farmer/alerts/new" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <TriangleAlert className="size-6 text-primary" aria-hidden />
            <span className="font-medium">Signaler une alerte terrain</span>
          </Link>
          <Link to="/farmer/requests/new" className={buttonVariants({ className: 'min-h-28 flex-col justify-center gap-2' })}>
            <Plus aria-hidden /> Déclarer une récolte
          </Link>
        </div>
      </div>
    </RequireRole>
  )
}
