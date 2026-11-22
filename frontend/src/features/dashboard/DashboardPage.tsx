import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Download, RefreshCw } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { RequireRole } from '@/components/RequireRole'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { selectClass } from '@/components/Field'
import { ApiError } from '@/lib/api'
import {
  DASHBOARD_ROLES,
  DEFAULT_DASHBOARD_FILTERS,
  dashboardHasData,
  dashboardState,
  downloadDashboardCsv,
  getDashboardFillRate,
  getDashboardLeadTime,
  getDashboardMillVolumes,
  getDashboardSummary,
  getDashboardYield,
  type DashboardFilters,
  type DashboardPeriod,
  type DashboardSummary,
  type FillRatePoint,
  type LeadTimePoint,
  type MillVolumePoint,
  type YieldPoint,
} from './dashboard'

export const DASHBOARD_PAGE_CLASS = 'min-w-0 flex flex-col gap-4'
export const DASHBOARD_FILTER_GRID_CLASS = 'grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4'
export const DASHBOARD_KPI_GRID_CLASS = 'grid grid-cols-2 gap-2 sm:grid-cols-4'
export const DASHBOARD_CHART_GRID_CLASS = 'grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2'

export function DashboardPage() {
  return (
    <RequireRole role={[...DASHBOARD_ROLES]}>
      <DashboardContent />
    </RequireRole>
  )
}

function DashboardContent() {
  const { activeRole, user } = useAuth()
  const [filters, setFilters] = useState<DashboardFilters>(DEFAULT_DASHBOARD_FILTERS)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const canSeeDetailedKpis = activeRole !== 'MILL_MANAGER'
  const assignedMillId = activeRole === 'MILL_MANAGER' ? user?.assignedMillId : undefined

  const summaryQuery = useQuery({
    queryKey: ['dashboard', 'summary', filters],
    queryFn: () => getDashboardSummary(filters),
  })
  const yieldQuery = useQuery({
    queryKey: ['dashboard', 'yield', 'delegation', filters],
    queryFn: () => getDashboardYield(filters, 'delegation'),
    enabled: canSeeDetailedKpis,
  })
  const orchardYieldQuery = useQuery({
    queryKey: ['dashboard', 'yield', 'orchard', filters],
    queryFn: () => getDashboardYield(filters, 'orchard'),
    enabled: canSeeDetailedKpis,
  })
  const leadTimeQuery = useQuery({
    queryKey: ['dashboard', 'lead-time', filters],
    queryFn: () => getDashboardLeadTime(filters),
    enabled: canSeeDetailedKpis,
  })
  const fillRateQuery = useQuery({
    queryKey: ['dashboard', 'fill-rate', filters],
    queryFn: () => getDashboardFillRate(filters),
  })
  const millVolumesQuery = useQuery({
    queryKey: ['dashboard', 'mill-volumes', filters, assignedMillId],
    queryFn: () => getDashboardMillVolumes(filters, assignedMillId),
  })

  const activeQueries: DashboardQuery[] = [summaryQuery, fillRateQuery, millVolumesQuery]
  if (canSeeDetailedKpis) activeQueries.push(yieldQuery, orchardYieldQuery, leadTimeQuery)
  const loading = activeQueries.some((query) => query.isPending)
  const errorQuery = activeQueries.find((query) => query.isError)
  const summary = summaryQuery.data
  const yieldPoints = yieldQuery.data ?? []
  const orchardYieldPoints = orchardYieldQuery.data ?? []
  const leadTimePoints = leadTimeQuery.data ?? []
  const fillRatePoints = fillRateQuery.data ?? []
  const millVolumePoints = millVolumesQuery.data ?? []
  const state = dashboardState({
    loading,
    error: errorQuery?.error ?? null,
    hasData: dashboardHasData(summary, yieldPoints, leadTimePoints, fillRatePoints, millVolumePoints),
  })

  async function exportCsv() {
    setExporting(true)
    setExportError(null)
    try {
      await downloadDashboardCsv(canSeeDetailedKpis ? 'yield' : 'mill-volumes', filters, assignedMillId)
    } catch (error) {
      setExportError(error instanceof ApiError ? error.detail : 'Impossible de télécharger l’export CSV.')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className={DASHBOARD_PAGE_CLASS} data-testid="dashboard-page">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="font-heading text-2xl font-bold text-primary">Tableau de bord</h1>
          <p className="text-sm text-muted-foreground">Suivez la saison de collecte et les performances des moulins.</p>
        </div>
        {state === 'ready' && (
          <Button type="button" className="h-11 w-full sm:w-fit" onClick={() => void exportCsv()} disabled={exporting}>
            <Download aria-hidden /> {exporting ? 'Export…' : 'Exporter en CSV'}
          </Button>
        )}
      </div>

      <DashboardFilters filters={filters} onChange={setFilters} />
      {exportError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{exportError}</p>}
      <DashboardStateView state={state} error={errorQuery?.error} />

      {state === 'ready' && summary && (
        <DashboardReady
          summary={summary}
          yieldPoints={yieldPoints}
          orchardYieldPoints={orchardYieldPoints}
          leadTimePoints={leadTimePoints}
          fillRatePoints={fillRatePoints}
          millVolumePoints={millVolumePoints}
          canSeeDetailedKpis={canSeeDetailedKpis}
          onRefresh={() => {
            void Promise.all(activeQueries.map((query) => query.refetch()))
          }}
          refreshing={activeQueries.some((query) => query.isFetching)}
        />
      )}
    </div>
  )
}

interface DashboardQuery {
  isPending: boolean
  isError: boolean
  error: unknown
  isFetching: boolean
  refetch: () => Promise<unknown>
}

export function DashboardStateView({ state, error }: { state: ReturnType<typeof dashboardState>; error?: unknown }) {
  if (state === 'ready') return null
  if (state === 'loading') {
    return <p data-testid="dashboard-loading" role="status" aria-live="polite" className="text-muted-foreground">Chargement du tableau de bord…</p>
  }
  if (state === 'error') {
    const detail = error instanceof ApiError ? error.detail : 'Impossible de charger les données du tableau de bord.'
    return <p data-testid="dashboard-error" role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{detail}</p>
  }
  return (
    <Card data-testid="dashboard-empty">
      <CardContent className="py-6">
        <p>Aucune donnée ne correspond à ces filtres.</p>
      </CardContent>
    </Card>
  )
}

function DashboardFilters({ filters, onChange }: { filters: DashboardFilters; onChange: (filters: DashboardFilters) => void }) {
  return (
    <Card>
      <CardHeader className="gap-1">
        <CardTitle>Filtres</CardTitle>
        <p className="text-sm text-muted-foreground">Affinez par saison, période, variété ou délégation.</p>
      </CardHeader>
      <CardContent className={DASHBOARD_FILTER_GRID_CLASS}>
        <label className="flex min-w-0 flex-col gap-2 text-sm font-medium">
          Saison
          <Input
            aria-label="Saison"
            className="h-11"
            value={filters.season}
            onChange={(event) => onChange({ ...filters, season: event.target.value })}
            placeholder="2026-2027"
          />
        </label>
        <label className="flex min-w-0 flex-col gap-2 text-sm font-medium">
          Période
          <select
            aria-label="Période"
            className={selectClass}
            value={filters.period}
            onChange={(event) => onChange({ ...filters, period: event.target.value as DashboardPeriod })}
          >
            <option value="season">Saison complète</option>
            <option value="last-7-days">7 derniers jours</option>
            <option value="last-30-days">30 derniers jours</option>
          </select>
        </label>
        <label className="flex min-w-0 flex-col gap-2 text-sm font-medium">
          Variété
          <Input
            aria-label="Variété"
            className="h-11"
            value={filters.variety}
            onChange={(event) => onChange({ ...filters, variety: event.target.value })}
            placeholder="Toutes les variétés"
          />
        </label>
        <label className="flex min-w-0 flex-col gap-2 text-sm font-medium">
          Délégation
          <Input
            aria-label="Délégation"
            className="h-11"
            value={filters.delegation}
            onChange={(event) => onChange({ ...filters, delegation: event.target.value })}
            placeholder="Toutes les délégations"
          />
        </label>
      </CardContent>
    </Card>
  )
}

function DashboardReady({
  summary,
  yieldPoints,
  orchardYieldPoints,
  leadTimePoints,
  fillRatePoints,
  millVolumePoints,
  canSeeDetailedKpis,
  onRefresh,
  refreshing,
}: {
  summary: DashboardSummary
  yieldPoints: YieldPoint[]
  orchardYieldPoints: YieldPoint[]
  leadTimePoints: LeadTimePoint[]
  fillRatePoints: FillRatePoint[]
  millVolumePoints: MillVolumePoint[]
  canSeeDetailedKpis: boolean
  onRefresh: () => void
  refreshing: boolean
}) {
  return (
    <>
      <div className={DASHBOARD_KPI_GRID_CLASS}>
        <KpiTile label="Tonnage de la saison" value={formatTonnes(summary.seasonTonnageKg)} />
        <KpiTile label="Rendement moyen" value={`${formatNumber(summary.avgYieldKgHa)} kg/ha`} />
        <KpiTile label="Délai moyen" value={`${formatNumber(summary.avgLeadTimeHours)} h`} />
        <KpiTile label="Taux de remplissage moyen" value={formatPercent(summary.avgFillRate)} />
      </div>

      <div className={DASHBOARD_CHART_GRID_CLASS}>
        {canSeeDetailedKpis && (
          <ChartCard title="Rendement par délégation" description="Rendement moyen en kilogrammes par hectare.">
            <YieldChart points={yieldPoints} />
          </ChartCard>
        )}
        {canSeeDetailedKpis && (
          <ChartCard title="Rendement par verger (top 20)" description="Meilleurs rendements de vergers pour les filtres choisis.">
            <YieldChart points={orchardYieldPoints.slice(0, 20)} />
          </ChartCard>
        )}
        <ChartCard title="Taux de remplissage par semaine" description="La ligne en pointillés marque l’objectif de 70 %.">
          <FillRateChart points={fillRatePoints} />
        </ChartCard>
        {canSeeDetailedKpis && (
          <ChartCard title="Délai de collecte" description="Heures moyennes et p90 par délégation.">
            <LeadTimeChart points={leadTimePoints} />
          </ChartCard>
        )}
        <ChartCard title="Volumes quotidiens des moulins" description="Volume d’olives reçu par moulin et par jour.">
          <MillVolumeChart points={millVolumePoints} />
        </ChartCard>
      </div>

      {millVolumePoints.length > 0 && <MillVolumeDetails points={millVolumePoints} />}

      {canSeeDetailedKpis && <OrchardTable points={orchardYieldPoints} />}

      <Button type="button" variant="outline" className="h-11 w-fit" onClick={onRefresh} disabled={refreshing}>
        <RefreshCw aria-hidden /> {refreshing ? 'Actualisation…' : 'Actualiser le tableau de bord'}
      </Button>
    </>
  )
}

function KpiTile({ label, value }: { label: string; value: string }) {
  return (
    <Card className="min-w-0">
      <CardContent className="flex min-h-28 flex-col justify-between gap-2 p-3 sm:p-4">
        <p className="text-xs font-medium text-muted-foreground sm:text-sm">{label}</p>
        <p className="break-words font-heading text-xl font-bold text-primary sm:text-2xl">{value}</p>
      </CardContent>
    </Card>
  )
}

function ChartCard({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <Card className="min-w-0">
      <CardHeader className="gap-1">
        <CardTitle>{title}</CardTitle>
        <p className="text-sm text-muted-foreground">{description}</p>
      </CardHeader>
      <CardContent className="min-w-0">
        <div className="h-64 min-w-0 w-full">{children}</div>
      </CardContent>
    </Card>
  )
}

function ChartEmpty() {
  return <p className="flex h-full items-center justify-center text-sm text-muted-foreground">Aucune donnée pour ce graphique.</p>
}

function YieldChart({ points }: { points: YieldPoint[] }) {
  if (points.length === 0) return <ChartEmpty />
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={points} margin={{ top: 8, right: 8, left: -16, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 11 }} width={48} />
        <Tooltip />
        <Bar dataKey="yieldKgHa" name="Rendement kg/ha" fill="var(--primary)" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

function FillRateChart({ points }: { points: FillRatePoint[] }) {
  if (points.length === 0) return <ChartEmpty />
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={points} margin={{ top: 8, right: 8, left: -16, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 11 }} width={48} tickFormatter={(value: number) => `${Math.round(value * 100)}%`} />
        <Tooltip />
        <ReferenceLine y={0.7} stroke="var(--destructive)" strokeDasharray="4 4" label="70%" />
        <Line type="monotone" dataKey="avgFillRate" name="Taux de remplissage moyen" stroke="var(--primary)" strokeWidth={2} dot={{ r: 3 }} />
      </LineChart>
    </ResponsiveContainer>
  )
}

function LeadTimeChart({ points }: { points: LeadTimePoint[] }) {
  if (points.length === 0) return <ChartEmpty />
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={points} margin={{ top: 8, right: 8, left: -16, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 11 }} width={48} />
        <Tooltip />
        <Legend />
        <Bar dataKey="avgLeadTimeHours" name="Heures moyennes" fill="var(--primary)" radius={[4, 4, 0, 0]} />
        <Bar dataKey="p90LeadTimeHours" name="Heures p90" fill="var(--secondary-foreground)" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

function MillVolumeChart({ points }: { points: MillVolumePoint[] }) {
  const { rows, mills } = millVolumeChartData(points)
  if (rows.length === 0) return <ChartEmpty />
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={rows} margin={{ top: 8, right: 8, left: -16, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 11 }} width={48} />
        <Tooltip />
        <Legend />
        {mills.map((mill, index) => (
          <Bar key={mill} dataKey={mill} name={mill} stackId="volume" fill={index % 2 === 0 ? 'var(--primary)' : 'var(--secondary-foreground)'} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

function MillVolumeDetails({ points }: { points: MillVolumePoint[] }) {
  return (
    <Card className="min-w-0">
      <CardHeader className="gap-1">
        <CardTitle>Détails qualité et capacité des moulins</CardTitle>
        <p className="text-sm text-muted-foreground">Effectifs par qualité et utilisation quotidienne de la capacité sur la période choisie.</p>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[38rem] text-left text-sm" aria-label="Détails qualité et capacité des moulins">
            <thead className="border-b text-muted-foreground">
              <tr>
                <th className="py-2 pr-3">Date</th>
                <th className="py-2 pr-3">Moulin</th>
                <th className="py-2 pr-3">Volume</th>
                <th className="py-2 pr-3">Qualité (E/B/M/F)</th>
                <th className="py-2">Utilisation de la capacité</th>
              </tr>
            </thead>
            <tbody>
              {points.map((point) => {
                const quality = point.qualityDistribution ?? {}
                return (
                  <tr key={`${point.date}-${point.millName}`} className="border-b last:border-0">
                    <td className="py-2 pr-3">{point.date}</td>
                    <td className="py-2 pr-3 font-medium">{point.millName}</td>
                    <td className="py-2 pr-3">{formatNumber(point.volumeKg)} kg</td>
                    <td className="py-2 pr-3">{quality.EXTRA ?? 0}/{quality.GOOD ?? 0}/{quality.AVERAGE ?? 0}/{quality.POOR ?? 0}</td>
                    <td className="py-2">{point.capacityUsePct === undefined ? '—' : `${formatNumber(point.capacityUsePct)}%`}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}

function OrchardTable({ points }: { points: YieldPoint[] }) {
  const sorted = [...points].sort((left, right) => right.yieldKgHa - left.yieldKgHa)
  const top = sorted.slice(0, 5).map((point) => ({ point, segment: 'Meilleurs' }))
  const bottom = sorted.length > 5 ? sorted.slice(-5).reverse().map((point) => ({ point, segment: 'Moins bons' })) : []
  const rows = [...top, ...bottom]

  return (
    <Card className="min-w-0">
      <CardHeader className="gap-1">
        <CardTitle>Meilleurs et moins bons vergers</CardTitle>
        <p className="text-sm text-muted-foreground">Classement des rendements pour les filtres choisis.</p>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <ChartEmpty />
        ) : (
          <>
            <ul className="flex flex-col gap-2 sm:hidden">
              {rows.map(({ point, segment }) => <OrchardCard key={`${segment}-${point.label}`} point={point} segment={segment} />)}
            </ul>
            <div className="hidden overflow-x-auto sm:block">
              <table className="w-full text-left text-sm" aria-label="Meilleurs et moins bons vergers">
                <thead className="border-b text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-3">Verger</th>
                    <th className="py-2 pr-3">Délégation</th>
                    <th className="py-2 pr-3">Rendement</th>
                    <th className="py-2">Classement</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(({ point, segment }) => (
                    <tr key={`${segment}-${point.label}`} className="border-b last:border-0">
                      <td className="py-2 pr-3 font-medium">{point.orchardName ?? point.label}</td>
                      <td className="py-2 pr-3 text-muted-foreground">{point.delegation ?? '—'}</td>
                      <td className="py-2 pr-3">{formatNumber(point.yieldKgHa)} kg/ha</td>
                      <td className="py-2">{segment}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function OrchardCard({ point, segment }: { point: YieldPoint; segment: string }) {
  return (
    <li className="flex min-w-0 items-center justify-between gap-3 rounded-lg border p-3">
      <div className="min-w-0">
        <p className="truncate font-medium">{point.orchardName ?? point.label}</p>
        <p className="truncate text-sm text-muted-foreground">{point.delegation ?? '—'} · {segment}</p>
      </div>
      <span className="shrink-0 text-sm font-medium">{formatNumber(point.yieldKgHa)} kg/ha</span>
    </li>
  )
}

function millVolumeChartData(points: MillVolumePoint[]): { rows: Record<string, string | number>[]; mills: string[] } {
  const mills = [...new Set(points.map((point) => point.millName))]
  const rows = new Map<string, Record<string, string | number>>()
  for (const point of points) {
    const row = rows.get(point.date) ?? { date: point.date }
    row[point.millName] = Number(row[point.millName] ?? 0) + point.volumeKg
    rows.set(point.date, row)
  }
  return { rows: [...rows.values()], mills }
}

function formatNumber(value: number): string {
  return value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })
}

function formatTonnes(valueKg: number): string {
  return `${formatNumber(valueKg / 1000)} t`
}

function formatPercent(value: number): string {
  return `${formatNumber(value * 100)}%`
}
