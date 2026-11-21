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
          <h1 className="font-heading text-2xl font-bold text-primary">Farmer home</h1>
          <p className="text-muted-foreground">Manage your orchards and collection requests.</p>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Link to="/farmer/requests" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <ClipboardList className="size-6 text-primary" aria-hidden />
            <span className="font-medium">My harvest requests</span>
          </Link>
          <Link to="/farmer/orchards" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <Sprout className="size-6 text-primary" aria-hidden />
            <span className="font-medium">My orchards</span>
          </Link>
          <Link to="/farmer/orchards/new" className={buttonVariants({ className: 'min-h-28 flex-col justify-center gap-2' })}>
            <Plus aria-hidden /> Add orchard
          </Link>
          <Link to="/farmer/alerts/new" className="flex min-h-28 flex-col justify-between rounded-xl bg-card p-4 ring-1 ring-foreground/10">
            <TriangleAlert className="size-6 text-primary" aria-hidden />
            <span className="font-medium">Report a field alert</span>
          </Link>
          <Link to="/farmer/requests/new" className={buttonVariants({ className: 'min-h-28 flex-col justify-center gap-2' })}>
            <Plus aria-hidden /> Declare a harvest
          </Link>
        </div>
      </div>
    </RequireRole>
  )
}
