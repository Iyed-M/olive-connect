import { CircleMarker, MapContainer, TileLayer, Tooltip } from 'react-leaflet'
import type { LatLngExpression } from 'leaflet'
import type { HarvestRequest } from '@/features/farmer/harvestRequests'
import { requestLocation } from './planning'

const SFAX: LatLngExpression = [34.7998, 10.7603]

export function PlanningMap({
  requests,
  selectedRequestIds,
  onToggle,
}: {
  requests: HarvestRequest[]
  selectedRequestIds: string[]
  onToggle?: (requestId: string) => void
}) {
  const selected = new Set(selectedRequestIds)
  const locatedRequests = requests.filter((request) => requestLocation(request))
  const firstLocation = locatedRequests[0] ? requestLocation(locatedRequests[0]) : null
  const center: LatLngExpression = firstLocation
    ? [firstLocation.coordinates[1], firstLocation.coordinates[0]]
    : SFAX

  return (
    <div className="overflow-hidden rounded-xl border">
      <MapContainer center={center} zoom={locatedRequests.length ? 11 : 9} scrollWheelZoom className="h-[22rem] w-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {locatedRequests.map((request) => {
          const location = requestLocation(request)
          if (!location) return null
          const isSelected = selected.has(request.id)
          return (
            <CircleMarker
              key={request.id}
              center={[location.coordinates[1], location.coordinates[0]]}
              radius={isSelected ? 11 : 8}
              pathOptions={{
                color: isSelected ? '#b45309' : '#4f642e',
                fillColor: isSelected ? '#f59e0b' : '#7c9a45',
                fillOpacity: isSelected ? 0.95 : 0.75,
                weight: isSelected ? 4 : 2,
              }}
              eventHandlers={onToggle ? { click: () => onToggle(request.id) } : undefined}
            >
              <Tooltip>
                {request.orchard.name} · {request.priorityScore.toFixed(1)}
              </Tooltip>
            </CircleMarker>
          )
        })}
      </MapContainer>
      {locatedRequests.length < requests.length && (
        <p className="border-t bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          {requests.length - locatedRequests.length} request(s) do not have map coordinates.
        </p>
      )}
    </div>
  )
}
