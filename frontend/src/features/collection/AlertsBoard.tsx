import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { RequireRole } from '@/components/RequireRole'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { selectClass } from '@/components/Field'
import { AlertsMap } from '@/features/alerts/AlertsMap'
import {
  ALERT_SEVERITY_LABEL,
  ALERT_STATUS_LABEL,
  ALERT_TYPE_LABEL,
  acknowledgeAlert,
  alertPhotoUrl,
  canCreateUrgentRequest,
  createUrgentRequestFromAlert,
  dismissAlert,
  listAlerts,
  resolveAlert,
  type AlertStatus,
  type AlertType,
  type FieldAlert,
} from '@/features/alerts/alerts'

type AlertStatusFilter = '' | AlertStatus
type AlertTypeFilter = '' | AlertType
type AlertActionName = 'acknowledge' | 'resolve' | 'dismiss' | 'urgent'

export function AlertsBoardPage() {
  return <RequireRole role="COLLECTION_MANAGER"><AlertsBoard /></RequireRole>
}

function AlertsBoard() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AlertStatusFilter>('')
  const [type, setType] = useState<AlertTypeFilter>('')
  const [notice, setNotice] = useState<string | null>(null)
  const query = useQuery({
    queryKey: ['alerts', { status, type }],
    queryFn: () => listAlerts({ status: status || undefined, type: type || undefined }),
  })
  const action = useMutation({
    mutationFn: ({ alert, name }: { alert: FieldAlert; name: AlertActionName }) => {
      if (name === 'acknowledge') return acknowledgeAlert(alert.id)
      if (name === 'resolve') return resolveAlert(alert.id)
      if (name === 'dismiss') return dismissAlert(alert.id)
      return createUrgentRequestFromAlert(alert.id)
    },
    onSuccess: async (_, variables) => {
      const messages: Record<AlertActionName, string> = {
        acknowledge: 'Alerte prise en charge.',
        resolve: 'Alerte résolue.',
        dismiss: 'Alerte écartée.',
        urgent: 'Demande urgente créée à partir de l’alerte.',
      }
      setNotice(messages[variables.name])
      await queryClient.invalidateQueries({ queryKey: ['alerts'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      await queryClient.invalidateQueries({ queryKey: ['harvest-requests'] })
    },
  })
  const error = query.error instanceof ApiError ? query.error.detail : query.error ? 'Impossible de charger les alertes.' : null
  const alerts = query.data ?? []

  function runAction(alert: FieldAlert, name: AlertActionName) {
    const labels: Record<AlertActionName, string> = {
      acknowledge: 'prendre en charge cette alerte',
      resolve: 'résoudre cette alerte',
      dismiss: 'écarter cette alerte',
      urgent: 'créer une demande urgente',
    }
    if ((name === 'dismiss' || name === 'urgent') && !window.confirm(`Voulez-vous ${labels[name]} ?`)) return
    setNotice(null)
    action.mutate({ alert, name })
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div>
        <h1 className="font-heading text-2xl font-bold text-primary">Alertes terrain</h1>
        <p className="text-sm text-muted-foreground">La file et la carte se mettent à jour dès qu’un événement arrive.</p>
      </div>
      {notice && <p role="status" className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm text-primary">{notice}</p>}
      {action.error && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{action.error instanceof ApiError ? action.error.detail : 'Action impossible. Réessayez.'}</p>}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-2 text-sm font-medium">
          Statut
          <select aria-label="Filtrer par statut d’alerte" className={selectClass} value={status} onChange={(event) => setStatus(event.target.value as AlertStatusFilter)}>
            <option value="">Toutes les alertes</option>
            {(Object.keys(ALERT_STATUS_LABEL) as AlertStatus[]).map((value) => <option key={value} value={value}>{ALERT_STATUS_LABEL[value]}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-2 text-sm font-medium">
          Type
          <select aria-label="Filtrer par type d’alerte" className={selectClass} value={type} onChange={(event) => setType(event.target.value as AlertTypeFilter)}>
            <option value="">Tous les types</option>
            {(Object.keys(ALERT_TYPE_LABEL) as AlertType[]).map((value) => <option key={value} value={value}>{ALERT_TYPE_LABEL[value]}</option>)}
          </select>
        </label>
      </div>

      {query.isPending && <p className="text-sm text-muted-foreground">Chargement des alertes…</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {query.data && alerts.length === 0 && <Card><CardContent className="py-6"><p>Aucune alerte ne correspond à ces filtres.</p></CardContent></Card>}
      {alerts.length > 0 && <AlertsMap alerts={alerts} />}
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        {alerts.map((alert) => <AlertCard key={alert.id} alert={alert} pending={action.isPending && action.variables?.alert.id === alert.id} onAction={(name) => runAction(alert, name)} />)}
      </div>
      {query.data && <Button variant="outline" className="w-fit" onClick={() => void query.refetch()} disabled={query.isFetching}>{query.isFetching ? 'Actualisation…' : 'Actualiser la file'}</Button>}
    </div>
  )
}

function AlertCard({ alert, pending, onAction }: { alert: FieldAlert; pending: boolean; onAction: (name: AlertActionName) => void }) {
  const canAcknowledge = alert.status === 'NEW'
  const canResolve = alert.status === 'ACKNOWLEDGED'
  const canDismiss = alert.status === 'NEW' || alert.status === 'ACKNOWLEDGED'
  const canUrgent = canCreateUrgentRequest(alert)

  return (
    <Card>
      <CardHeader className="gap-2">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle>{alert.orchardName}</CardTitle>
            <p className="text-xs text-muted-foreground">{ALERT_TYPE_LABEL[alert.type]} · {new Date(alert.createdAt).toLocaleString('fr-TN')}</p>
          </div>
          <span className="rounded-full bg-secondary px-2 py-1 text-xs font-medium">{ALERT_STATUS_LABEL[alert.status]}</span>
        </div>
        <p className="text-sm">Gravité : <strong>{ALERT_SEVERITY_LABEL[alert.severity]}</strong></p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {alert.description && <p className="text-sm text-muted-foreground">{alert.description}</p>}
        {alert.locationMismatch && <p className="rounded-lg border border-amber-400/50 bg-amber-50 p-2 text-sm text-amber-900">Position du signalement à plus de 2 km du verger.</p>}
        {alert.photoUrls?.map((photo) => <a key={photo} href={alertPhotoUrl(photo)} target="_blank" rel="noreferrer" className="text-sm text-primary underline">Voir la photo</a>)}
        <div className="flex flex-wrap gap-2">
          {canAcknowledge && <Button disabled={pending} onClick={() => onAction('acknowledge')}>{pending ? 'En cours…' : 'Prendre en charge'}</Button>}
          {canResolve && <Button disabled={pending} onClick={() => onAction('resolve')}>{pending ? 'En cours…' : 'Résoudre'}</Button>}
          {canDismiss && <Button variant="destructive" disabled={pending} onClick={() => onAction('dismiss')}>Écarter</Button>}
          {canUrgent && <Button variant="outline" disabled={pending} onClick={() => onAction('urgent')}>Créer une demande urgente</Button>}
        </div>
      </CardContent>
    </Card>
  )
}
