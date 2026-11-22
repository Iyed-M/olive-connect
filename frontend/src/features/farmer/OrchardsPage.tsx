import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { MapPin, Plus } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { OrchardMap } from './OrchardMap'
import { listOrchards } from './orchards'

export function OrchardsPage() {
  return <RequireRole role="FARMER"><OrchardList /></RequireRole>
}

function OrchardList() {
  const query = useQuery({ queryKey: ['orchards'], queryFn: listOrchards })
  const error = query.error instanceof ApiError ? query.error : null

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">Mes vergers</h1>
          <p className="text-sm text-muted-foreground">Vos parcelles et leur position actuelle.</p>
        </div>
        <Link to="/farmer/orchards/new" className={buttonVariants({ className: 'h-11' })}><Plus aria-hidden /> Ajouter</Link>
      </div>

      {query.isPending && <p className="text-muted-foreground">Chargement des vergers…</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">{error?.detail ?? 'Impossible de charger vos vergers.'}</p>}
      {query.data && query.data.length === 0 && (
        <Card><CardContent className="flex flex-col items-start gap-3 py-6">
          <p>Aucun verger pour le moment. Ajoutez votre premier verger pour commencer.</p>
          <Link to="/farmer/orchards/new" className={buttonVariants()}>Ajouter un verger</Link>
        </CardContent></Card>
      )}
      {query.data && query.data.length > 0 && (
        <>
          <OrchardMap orchards={query.data} />
          <ul className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {query.data.map((orchard) => (
              <li key={orchard.id}>
                <Card>
                  <CardContent className="flex flex-col gap-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h2 className="font-heading text-lg font-semibold">{orchard.name}</h2>
                        <p className="flex items-center gap-1 text-xs text-muted-foreground"><MapPin className="size-3" aria-hidden /> {orchard.delegation.replaceAll('_', ' ')}</p>
                      </div>
                      <Link to="/farmer/orchards/$id" params={{ id: orchard.id }} className="text-sm text-primary underline">Modifier</Link>
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-sm">
                      <span><strong>{orchard.areaHa.toFixed(2)}</strong><br /><small className="text-muted-foreground">ha</small></span>
                      <span><strong>{orchard.treeCount}</strong><br /><small className="text-muted-foreground">arbres</small></span>
                      <span><strong>{orchard.plots.length}</strong><br /><small className="text-muted-foreground">parcelles</small></span>
                    </div>
                    <p className="text-xs text-muted-foreground">Variété principale : {orchard.mainVarietyCode}</p>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>
        </>
      )}
      {query.data && query.data.length > 0 && <Button variant="outline" className="h-11 w-fit" onClick={() => void query.refetch()}>Actualiser</Button>}
    </div>
  )
}
