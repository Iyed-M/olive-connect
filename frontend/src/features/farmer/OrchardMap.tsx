import { CircleMarker, MapContainer, TileLayer, useMapEvents } from 'react-leaflet'
import type { LatLngExpression } from 'leaflet'
import type { GeoPoint, Orchard } from './orchards'

const SFAX: LatLngExpression = [34.7998, 10.7603]

function Picker({ onChange }: { onChange: (point: GeoPoint) => void }) {
  useMapEvents({
    click(event) {
      onChange({ type: 'Point', coordinates: [event.latlng.lng, event.latlng.lat] })
    },
  })
  return null
}

export function OrchardMap({
  orchards = [],
  value,
  onChange,
  className = '',
}: {
  orchards?: Orchard[]
  value?: GeoPoint
  onChange?: (point: GeoPoint) => void
  className?: string
}) {
  const center: LatLngExpression = value
    ? [value.coordinates[1], value.coordinates[0]]
    : orchards[0]
      ? [orchards[0].location.coordinates[1], orchards[0].location.coordinates[0]]
      : SFAX

  return (
    <div className={`overflow-hidden rounded-xl border ${className}`}>
      <MapContainer center={center} zoom={value || orchards.length ? 12 : 9} scrollWheelZoom className="h-64 w-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {onChange && <Picker onChange={onChange} />}
        {orchards.map((orchard) => (
          <CircleMarker
            key={orchard.id}
            center={[orchard.location.coordinates[1], orchard.location.coordinates[0]]}
            radius={9}
            pathOptions={{ color: '#4f642e', fillColor: '#7c9a45', fillOpacity: 0.85 }}
          />
        ))}
        {value && !orchards.length && (
          <CircleMarker
            center={[value.coordinates[1], value.coordinates[0]]}
            radius={9}
            pathOptions={{ color: '#b45309', fillColor: '#f59e0b', fillOpacity: 0.85 }}
          />
        )}
      </MapContainer>
    </div>
  )
}
