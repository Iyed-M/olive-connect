import { useEffect, useRef, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import L, { type LatLngExpression, type Map as LeafletMap } from 'leaflet'
import { DELEGATION_LABEL, type Delegation } from '@/admin/users'
import { useAuth } from '@/auth/AuthContext'
import { ROLE_HOME } from '@/auth/roles'
import { buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { listVarieties } from '@/features/farmer/orchards'
import {
  getPublicMap,
  NO_FILTERS,
  STATUS_COLOR,
  STATUS_LABEL,
  type Bbox,
  type HarvestStatus,
  type MapFilters,
  type PublicFeature,
} from './publicMap'

const SFAX: LatLngExpression = [34.7998, 10.7603]
const DEBOUNCE_MS = 300
// The API refuses a bbox over 4 degrees. At zoom 10 a screen up to ~3000 px wide stays under it.
const MIN_ZOOM = 10

function readBbox(map: LeafletMap): Bbox {
  const b = map.getBounds()
  return [b.getWest(), b.getSouth(), b.getEast(), b.getNorth()]
}

type MapView = { bbox: Bbox; zoom: number }
const readView = (map: LeafletMap): MapView => ({ bbox: readBbox(map), zoom: map.getZoom() })

// Reports the visible bbox on load and after each move, debounced (docs/08 §5).
function BboxWatcher({ onChange }: { onChange: (view: MapView) => void }) {
  const map = useMap()
  const timer = useRef<number | undefined>(undefined)
  useEffect(() => {
    onChange(readView(map))
    return () => window.clearTimeout(timer.current)
  }, [map, onChange])
  useMapEvents({
    moveend() {
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => onChange(readView(map)), DEBOUNCE_MS)
    },
  })
  return null
}

function ClusterMarker({ feature }: { feature: PublicFeature }) {
  const [lng, lat] = feature.geometry.coordinates
  const count = feature.properties.count ?? 0
  const icon = L.divIcon({
    className: '',
    html: `<div style="background:#4f642e;color:#fff;border-radius:9999px;width:36px;height:36px;display:flex;align-items:center;justify-content:center;font-weight:600;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)">${count}</div>`,
    iconSize: [36, 36],
  })
  return (
    <Marker position={[lat, lng]} icon={icon} title={`${count} orchards`}>
      <Popup>{count} orchards. Zoom in to see them.</Popup>
    </Marker>
  )
}

function OrchardMarker({ feature }: { feature: PublicFeature }) {
  const [lng, lat] = feature.geometry.coordinates
  const { varietyCode, harvestStatus, maturityIndex } = feature.properties
  const color = (harvestStatus && STATUS_COLOR[harvestStatus]) || STATUS_COLOR.NOT_HARVESTED
  // Maturity (0-7 scale) is shown by the fill opacity.
  const opacity = 0.3 + 0.7 * Math.min(Math.max((maturityIndex ?? 0) / 7, 0), 1)
  return (
    <CircleMarker center={[lat, lng]} radius={9} pathOptions={{ color, fillColor: color, fillOpacity: opacity, weight: 3 }}>
      <Popup>
        Variety: {varietyCode}
        <br />
        Status: {harvestStatus ? STATUS_LABEL[harvestStatus] : '-'}
        <br />
        Maturity: {maturityIndex}
      </Popup>
    </CircleMarker>
  )
}

const selectClass = 'h-9 w-full rounded-lg border border-input bg-background px-2 text-sm'
const STATUSES = Object.keys(STATUS_LABEL) as HarvestStatus[]

export function PublicMapPage() {
  const { activeRole } = useAuth()
  const [view, setView] = useState<MapView | null>(null)
  const [filters, setFilters] = useState<MapFilters>(NO_FILTERS)
  const set = (key: keyof MapFilters) => (value: string) => setFilters((f) => ({ ...f, [key]: value }))

  const varieties = useQuery({ queryKey: ['varieties'], queryFn: listVarieties, staleTime: Infinity })
  const map = useQuery({
    queryKey: ['map-public', view, filters],
    queryFn: () => getPublicMap(view!.bbox, filters, view!.zoom),
    enabled: view !== null,
    placeholderData: keepPreviousData,
  })

  return (
    <div className="flex h-screen flex-col">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">OliveConnect</h1>
          <p className="text-sm text-muted-foreground">Olive orchards of the Sfax region.</p>
        </div>
        <div className="flex gap-2">
          <Link to="/register" className={buttonVariants({ variant: 'outline' })}>
            Join the cooperative
          </Link>
          {activeRole ? (
            <Link to={ROLE_HOME[activeRole]} className={buttonVariants()}>
              Go to my home
            </Link>
          ) : (
            <Link to="/login" className={buttonVariants()}>
              Log in
            </Link>
          )}
        </div>
      </header>

      <form
        className="grid grid-cols-2 gap-3 border-b px-4 py-3 sm:grid-cols-5"
        onSubmit={(e) => e.preventDefault()}
        aria-label="Map filters"
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-variety">Variety</Label>
          <select id="f-variety" className={selectClass} value={filters.variety} onChange={(e) => set('variety')(e.target.value)}>
            <option value="">All</option>
            {(varieties.data ?? []).map((v) => (
              <option key={v.code} value={v.code}>
                {v.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-status">Harvest status</Label>
          <select id="f-status" className={selectClass} value={filters.status} onChange={(e) => set('status')(e.target.value)}>
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-delegation">Delegation</Label>
          <select id="f-delegation" className={selectClass} value={filters.delegation} onChange={(e) => set('delegation')(e.target.value)}>
            <option value="">All</option>
            {(Object.keys(DELEGATION_LABEL) as Delegation[]).map((d) => (
              <option key={d} value={d}>
                {DELEGATION_LABEL[d]}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-min">Maturity min</Label>
          <Input id="f-min" type="number" min={0} step="0.1" value={filters.maturityMin} onChange={(e) => set('maturityMin')(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-max">Maturity max</Label>
          <Input id="f-max" type="number" min={0} step="0.1" value={filters.maturityMax} onChange={(e) => set('maturityMax')(e.target.value)} />
        </div>
      </form>

      <div className="relative min-h-0 flex-1">
        <MapContainer center={SFAX} zoom={11} minZoom={MIN_ZOOM} scrollWheelZoom className="h-full w-full">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <BboxWatcher onChange={setView} />
          {(map.data?.features ?? []).map((feature, index) =>
            feature.properties.count !== undefined ? (
              <ClusterMarker key={`c-${index}`} feature={feature} />
            ) : (
              <OrchardMarker key={feature.properties.id ?? index} feature={feature} />
            ),
          )}
        </MapContainer>

        <div className="absolute bottom-4 left-4 z-[1000] rounded-lg border bg-background/95 p-3 text-sm shadow">
          {STATUSES.map((s) => (
            <div key={s} className="flex items-center gap-2">
              <span className="inline-block size-3 rounded-full" style={{ background: STATUS_COLOR[s] }} />
              {STATUS_LABEL[s]}
            </div>
          ))}
          <div className="mt-1 text-xs text-muted-foreground">Fainter = less mature</div>
        </div>
        {map.isError && (
          <p role="alert" className="absolute left-1/2 top-4 z-[1000] -translate-x-1/2 rounded-lg bg-destructive px-3 py-2 text-sm text-white">
            Cannot load the map data.
          </p>
        )}
      </div>
    </div>
  )
}
