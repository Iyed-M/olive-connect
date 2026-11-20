import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { OrchardMap } from './OrchardMap'
import {
  createOrchard,
  listVarieties,
  updateOrchard,
  type GeoPoint,
  type Orchard,
  type OrchardWriteBody,
  type Plot,
} from './orchards'

const DEFAULT_LOCATION: GeoPoint = { type: 'Point', coordinates: [10.7603, 34.7998] }
const DEFAULT_PLOT: Plot = { code: 'P1', varietyCode: 'CHEMLALI', areaHa: 1, treeCount: 100, plantingYear: 2000, irrigated: false }
const DELEGATIONS = [
  'SFAX_VILLE',
  'SFAX_OUEST',
  'SFAX_SUD',
  'SAKIET_EZZIT',
  'SAKIET_EDDAIER',
  'THYNA',
  'AGAREB',
  'JEBINIANA',
  'EL_AMRA',
  'EL_HENCHA',
  'MENZEL_CHAKER',
  'GHRAIBA',
  'BIR_ALI_BEN_KHALIFA',
  'SKHIRA',
  'MAHRES',
  'KERKENNAH',
]

function displayDelegation(code: string) {
  return code.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, (letter) => letter.toUpperCase())
}

function initialPlots(orchard?: Orchard) {
  return orchard?.plots.length ? orchard.plots : [DEFAULT_PLOT]
}

export function OrchardForm({ orchard }: { orchard?: Orchard }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState(orchard?.name ?? '')
  const [delegation, setDelegation] = useState(orchard?.delegation ?? 'SFAX_VILLE')
  const [location, setLocation] = useState<GeoPoint>(orchard?.location ?? DEFAULT_LOCATION)
  const [plots, setPlots] = useState<Plot[]>(initialPlots(orchard))
  const [gpsMessage, setGpsMessage] = useState('')

  const varieties = useQuery({ queryKey: ['varieties'], queryFn: listVarieties, staleTime: Infinity })
  const mutation = useMutation({
    mutationFn: (body: OrchardWriteBody) => (orchard ? updateOrchard(orchard.id, body) : createOrchard(body)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['orchards'] })
      await navigate({ to: '/farmer/orchards' })
    },
  })

  useEffect(() => {
    if (orchard || !navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => setLocation({ type: 'Point', coordinates: [coords.longitude, coords.latitude] }),
      () => setGpsMessage('GPS was not available. Tap the map to choose the position.'),
      { enableHighAccuracy: true, timeout: 8000 },
    )
  }, [orchard])

  const error = mutation.error instanceof ApiError ? mutation.error : null
  const varietyOptions = useMemo(() => varieties.data ?? [], [varieties.data])

  function updatePlot(index: number, patch: Partial<Plot>) {
    setPlots((current) => current.map((plot, i) => (i === index ? { ...plot, ...patch } : plot)))
  }

  function addPlot() {
    setPlots((current) => [...current, { ...DEFAULT_PLOT, code: `P${current.length + 1}` }])
  }

  function removePlot(index: number) {
    if (plots.length === 1) return
    setPlots((current) => current.filter((_, i) => i !== index))
  }

  function save(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({ name: name.trim(), delegation, location, plots })
  }

  return (
    <form onSubmit={save} className="flex flex-col gap-4" noValidate>
      <div className="flex items-center justify-between gap-2">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary">{orchard ? 'Edit orchard' : 'Add orchard'}</h1>
          <p className="text-sm text-muted-foreground">Choose the position, then add each plot.</p>
        </div>
        <Link to="/farmer/orchards" className="text-sm text-primary underline">Cancel</Link>
      </div>

      <Card>
        <CardHeader><CardTitle>Orchard details</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="orchard-name">Name</Label>
            <Input id="orchard-name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={120} />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="delegation">Delegation</Label>
            <select
              id="delegation"
              value={delegation}
              onChange={(event) => setDelegation(event.target.value)}
              className="h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm"
            >
              {DELEGATIONS.map((code) => <option key={code} value={code}>{displayDelegation(code)}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
              <Label>GPS position</Label>
              <span className="font-mono text-xs text-muted-foreground">
                {location.coordinates[1].toFixed(5)}, {location.coordinates[0].toFixed(5)}
              </span>
            </div>
            <OrchardMap value={location} onChange={setLocation} />
            <p className="text-xs text-muted-foreground">Tap the map to adjust the marker. {gpsMessage}</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between gap-2">
          <CardTitle>Plots</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={addPlot}><Plus aria-hidden /> Add plot</Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {plots.map((plot, index) => (
            <div key={plot.code} className="flex flex-col gap-3 rounded-lg border p-3">
              <div className="flex items-center justify-between gap-2">
                <h2 className="font-medium">Plot {index + 1}</h2>
                <Button type="button" variant="ghost" size="icon" aria-label={`Remove plot ${index + 1}`} disabled={plots.length === 1} onClick={() => removePlot(index)}>
                  <Trash2 aria-hidden />
                </Button>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plot-${index}-code`}>Code</Label>
                  <Input id={`plot-${index}-code`} value={plot.code} onChange={(event) => updatePlot(index, { code: event.target.value })} required />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plot-${index}-variety`}>Variety</Label>
                  <select
                    id={`plot-${index}-variety`}
                    value={plot.varietyCode}
                    onChange={(event) => updatePlot(index, { varietyCode: event.target.value })}
                    className="h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm"
                  >
                    {varietyOptions.length === 0 && <option value={plot.varietyCode}>{plot.varietyCode}</option>}
                    {varietyOptions.map((variety) => <option key={variety.code} value={variety.code}>{variety.name}</option>)}
                  </select>
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plot-${index}-area`}>Area (ha)</Label>
                  <Input id={`plot-${index}-area`} type="number" min="0.01" step="0.01" value={plot.areaHa} onChange={(event) => updatePlot(index, { areaHa: Number(event.target.value) })} required />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plot-${index}-trees`}>Trees</Label>
                  <Input id={`plot-${index}-trees`} type="number" min="1" step="1" value={plot.treeCount} onChange={(event) => updatePlot(index, { treeCount: Number(event.target.value) })} required />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor={`plot-${index}-year`}>Planting year</Label>
                  <Input id={`plot-${index}-year`} type="number" min="1800" max="2100" value={plot.plantingYear} onChange={(event) => updatePlot(index, { plantingYear: Number(event.target.value) })} required />
                </div>
                <label className="flex min-h-11 items-center gap-3 text-sm">
                  <input type="checkbox" className="size-5" checked={plot.irrigated} onChange={(event) => updatePlot(index, { irrigated: event.target.checked })} />
                  Irrigated
                </label>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {error && <p role="alert" className="text-sm text-destructive">{error.detail}</p>}
      {mutation.isError && !error && <p role="alert" className="text-sm text-destructive">Could not save the orchard.</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Saving...' : 'Save orchard'}</Button>
        <Link to="/farmer/orchards" className="inline-flex h-11 items-center rounded-lg border px-4 text-sm">Back to orchards</Link>
      </div>
    </form>
  )
}
