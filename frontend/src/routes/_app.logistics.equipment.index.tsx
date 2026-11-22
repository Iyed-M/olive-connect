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
import {
  EQUIPMENT_STATUS_LABEL,
  EQUIPMENT_TYPE_LABEL,
  equipmentSummary,
  listEquipment,
  type EquipmentFilters,
  type EquipmentStatus,
  type EquipmentType,
} from '@/logistics/resources'

export const Route = createFileRoute('/_app/logistics/equipment/')({ component: Page })

function Page() {
  return (
    <RequireRole role={['LOGISTICS_MANAGER', 'ADMIN']}>
      <EquipmentList />
    </RequireRole>
  )
}

function EquipmentList() {
  const [filters, setFilters] = useState<EquipmentFilters>({ type: '', status: '', page: 0 })
  const query = useQuery({
    queryKey: ['equipment', filters],
    queryFn: () => listEquipment(filters),
    placeholderData: keepPreviousData,
  })
  const data = query.data

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <h1 className="font-heading text-2xl font-bold text-primary">Équipements</h1>
        <Link to="/logistics/equipment/new" className={buttonVariants({ className: 'h-11' })}>
          <Plus aria-hidden /> Nouvel équipement
        </Link>
      </div>
      <Link to="/logistics/workers" className="w-fit text-sm text-primary hover:underline">
        Aller aux ouvriers
      </Link>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <select
          aria-label="Filtrer par type"
          value={filters.type}
          onChange={(e) => setFilters({ ...filters, type: e.target.value as EquipmentFilters['type'], page: 0 })}
          className={selectClass}
        >
          <option value="">Tous les types</option>
          {(Object.keys(EQUIPMENT_TYPE_LABEL) as EquipmentType[]).map((t) => (
            <option key={t} value={t}>
              {EQUIPMENT_TYPE_LABEL[t]}
            </option>
          ))}
        </select>
        <select
          aria-label="Filtrer par statut"
          value={filters.status}
          onChange={(e) => setFilters({ ...filters, status: e.target.value as EquipmentFilters['status'], page: 0 })}
          className={selectClass}
        >
          <option value="">Tous les statuts</option>
          {(Object.keys(EQUIPMENT_STATUS_LABEL) as EquipmentStatus[]).map((s) => (
            <option key={s} value={s}>
              {EQUIPMENT_STATUS_LABEL[s]}
            </option>
          ))}
        </select>
      </div>

      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Impossible de charger les équipements.
        </p>
      )}
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {data && data.content.length === 0 && <p className="text-muted-foreground">Aucun équipement trouvé.</p>}

      {data && data.content.length > 0 && (
        <>
          {/* Cards below md */}
          <ul className="flex flex-col gap-2 md:hidden">
            {data.content.map((e) => (
              <li key={e.id}>
                <Link to="/logistics/equipment/$id" params={{ id: e.id }}>
                  <Card>
                    <CardContent className="flex flex-col gap-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">{e.code}</span>
                        <StatusBadge status={e.status} />
                      </div>
                      <span className="text-sm text-muted-foreground">
                        {EQUIPMENT_TYPE_LABEL[e.type]} · {equipmentSummary(e)}
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
                <th className="py-2">Code</th>
                <th>Type</th>
                <th>Détails</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((e) => (
                <tr key={e.id} className="border-b">
                  <td className="py-2">
                    <Link to="/logistics/equipment/$id" params={{ id: e.id }} className="font-medium text-primary hover:underline">
                      {e.code}
                    </Link>
                  </td>
                  <td>{EQUIPMENT_TYPE_LABEL[e.type]}</td>
                  <td>{equipmentSummary(e)}</td>
                  <td>
                    <StatusBadge status={e.status} />
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

function StatusBadge({ status }: { status: EquipmentStatus }) {
  return <Badge variant={status === 'AVAILABLE' ? 'default' : 'outline'}>{EQUIPMENT_STATUS_LABEL[status]}</Badge>
}
