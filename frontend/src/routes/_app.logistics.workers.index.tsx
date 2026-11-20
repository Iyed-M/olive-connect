import { useState } from 'react'
import { Link, createFileRoute } from '@tanstack/react-router'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { RequireRole } from '@/components/RequireRole'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { selectClass } from '@/components/Field'
import { Pager } from '@/logistics/Pager'
import { listWorkers, type WorkerFilters } from '@/logistics/resources'

export const Route = createFileRoute('/_app/logistics/workers/')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'ADMIN']}>
      <WorkersList />
    </RequireRole>
  )
}

function WorkersList() {
  const [filters, setFilters] = useState<WorkerFilters>({ active: '', page: 0 })
  const query = useQuery({
    queryKey: ['workers', filters],
    queryFn: () => listWorkers(filters),
    placeholderData: keepPreviousData,
  })
  const data = query.data

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <h1 className="font-heading text-2xl font-bold text-primary">Workers</h1>
        <Link to="/logistics/workers/new" className={buttonVariants({ className: 'h-11' })}>
          <Plus aria-hidden /> New worker
        </Link>
      </div>
      <Link to="/logistics/equipment" className="w-fit text-sm text-primary hover:underline">
        Go to equipment
      </Link>

      <select
        aria-label="Filter by status"
        value={filters.active}
        onChange={(e) => setFilters({ active: e.target.value as WorkerFilters['active'], page: 0 })}
        className={selectClass}
      >
        <option value="">Active and inactive</option>
        <option value="true">Active</option>
        <option value="false">Inactive</option>
      </select>

      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the workers.
        </p>
      )}
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {data && data.content.length === 0 && <p className="text-muted-foreground">No workers found.</p>}

      {data && data.content.length > 0 && (
        <>
          {/* Cards below md */}
          <ul className="flex flex-col gap-2 md:hidden">
            {data.content.map((w) => (
              <li key={w.id}>
                <Link to="/logistics/workers/$id" params={{ id: w.id }}>
                  <Card>
                    <CardContent className="flex flex-col gap-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">
                          {w.firstName} {w.lastName}
                        </span>
                        <ActiveBadge active={w.active} />
                      </div>
                      <span className="text-sm text-muted-foreground">
                        {w.skills.join(', ') || 'No skills'} · {w.dailyRate} TND/day
                      </span>
                    </CardContent>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>

          {/* Table from md */}
          <table className="hidden w-full text-left text-sm md:table">
            <thead className="border-b text-muted-foreground">
              <tr>
                <th className="py-2">Name</th>
                <th>CIN</th>
                <th>Skills</th>
                <th>Daily rate</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((w) => (
                <tr key={w.id} className="border-b">
                  <td className="py-2">
                    <Link to="/logistics/workers/$id" params={{ id: w.id }} className="font-medium text-primary hover:underline">
                      {w.firstName} {w.lastName}
                    </Link>
                  </td>
                  <td>{w.cin}</td>
                  <td>{w.skills.join(', ')}</td>
                  <td>{w.dailyRate} TND</td>
                  <td>
                    <ActiveBadge active={w.active} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <Pager page={filters.page} total={data.totalElements} onChange={(page) => setFilters({ ...filters, page })} />
        </>
      )}
    </>
  )
}

function ActiveBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? 'default' : 'outline'}>{active ? 'Active' : 'Inactive'}</Badge>
}
