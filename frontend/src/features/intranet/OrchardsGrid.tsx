import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { listOrchards } from '@/features/farmer/orchards'

// Intranet data grid of all orchards (FR-6.5). Staff open a row to correct it.
export function OrchardsGrid() {
  const [search, setSearch] = useState('')
  const query = useQuery({ queryKey: ['orchards', 'staff'], queryFn: listOrchards })
  const text = search.trim().toLowerCase()
  const rows = (query.data ?? []).filter(
    (o) => !text || o.name.toLowerCase().includes(text) || (o.owner.displayName ?? '').toLowerCase().includes(text),
  )

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Orchards</h1>
      <Input aria-label="Search" placeholder="Search by orchard or owner" value={search} onChange={(e) => setSearch(e.target.value)} />
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">Could not load the orchards.</p>}
      {query.data && rows.length === 0 && <p className="text-muted-foreground">No orchard found.</p>}
      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b text-muted-foreground">
              <tr>
                <th className="py-2">Name</th>
                <th>Owner</th>
                <th>Delegation</th>
                <th>Area (ha)</th>
                <th>Trees</th>
                <th>Status</th>
                <th>Corrections</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((o) => (
                <tr key={o.id} className="border-b">
                  <td className="py-2">
                    <Link to="/intranet/orchards/$id" params={{ id: o.id }} className="font-medium text-primary hover:underline">
                      {o.name}
                    </Link>
                  </td>
                  <td>{o.owner.displayName ?? '-'}</td>
                  <td>{o.delegation}</td>
                  <td>{o.areaHa}</td>
                  <td>{o.treeCount}</td>
                  <td>
                    <Badge variant="outline">{o.harvestStatus}</Badge>
                  </td>
                  <td>{o.corrections?.length ?? 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
