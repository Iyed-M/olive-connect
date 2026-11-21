import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet'
import type { LatLngExpression } from 'leaflet'
import { ALERT_SEVERITY_LABEL, ALERT_STATUS_LABEL, ALERT_TYPE_LABEL, type FieldAlert } from './alerts'

const SFAX: LatLngExpression = [34.7998, 10.7603]

export function AlertsMap({ alerts }: { alerts: FieldAlert[] }) {
  const located = alerts.filter((alert) => alert.location)
  const firstLocation = located[0]?.location
  const center: LatLngExpression = firstLocation
    ? [firstLocation.coordinates[1], firstLocation.coordinates[0]]
    : SFAX

  return (
    <div className="overflow-hidden rounded-xl border">
      <MapContainer center={center} zoom={located.length ? 11 : 9} scrollWheelZoom className="h-[22rem] w-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {located.map((alert) => {
          const location = alert.location!
          return (
            <CircleMarker
              key={alert.id}
              center={[location.coordinates[1], location.coordinates[0]]}
              radius={alert.status === 'NEW' ? 11 : 8}
              pathOptions={{
                color: '#991b1b',
                fillColor: alert.status === 'NEW' ? '#ef4444' : '#f97316',
                fillOpacity: 0.88,
                weight: alert.status === 'NEW' ? 4 : 2,
              }}
            >
              <Popup>
                <strong>{alert.orchardName}</strong>
                <br />
                {ALERT_TYPE_LABEL[alert.type]} · {ALERT_STATUS_LABEL[alert.status]}
                <br />
                {ALERT_SEVERITY_LABEL[alert.severity]}
              </Popup>
            </CircleMarker>
          )
        })}
      </MapContainer>
      {located.length < alerts.length && (
        <p className="border-t bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          {alerts.length - located.length} alerte(s) sans position ne sont pas affichées sur la carte.
        </p>
      )}
    </div>
  )
}
