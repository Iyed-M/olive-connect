import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch, type SubmitHandler } from 'react-hook-form'
import { Field } from '@/components/Field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  buildRoundPayload,
  createRound,
  formatKg,
  formatRoundWarning,
  getResourceAvailability,
  millCapacityWarning,
  planningErrorMessage,
  resourceLabel,
  teamLeaderLabel,
  toAvailabilityRange,
  updateRound,
  validateRoundSelection,
  type CollectionRound,
  type MillDto,
  type PlanningSuggestions,
  type ResourceAvailability,
  type RoundMutationResult,
  type RoundWriteValues,
} from './planning'
import type { HarvestRequest } from '@/features/farmer/harvestRequests'

interface RoundFormValues {
  date: string
  startTime: string
  endTime: string
  teamLeaderId: string
  workerIds: string[]
  equipmentIds: string[]
  millId: string
}

export function RoundForm({
  requests,
  selectedRequestIds,
  suggestions,
  mills,
  existingRound,
  defaultDate,
  onDateChange,
  onSaved,
  onCancelEdit,
}: {
  requests: HarvestRequest[]
  selectedRequestIds: string[]
  suggestions?: PlanningSuggestions
  mills: MillDto[]
  existingRound?: CollectionRound | null
  defaultDate: string
  onDateChange: (date: string) => void
  onSaved: (result: RoundMutationResult) => void
  onCancelEdit?: () => void
}) {
  const queryClient = useQueryClient()
  const [selectionErrors, setSelectionErrors] = useState<string[]>([])
  const defaults = useMemo(() => roundDefaults(existingRound, defaultDate), [defaultDate, existingRound])
  const { control, register, handleSubmit, reset, formState: { errors } } = useForm<RoundFormValues>({ defaultValues: defaults })
  const [date, startTime, endTime] = useWatch({ control, name: ['date', 'startTime', 'endTime'] })
  const selectedMillId = useWatch({ control, name: 'millId' })
  const selectedWorkerIds = useWatch({ control, name: 'workerIds' }) ?? []
  const selectedEquipmentIds = useWatch({ control, name: 'equipmentIds' }) ?? []
  const range = toAvailabilityRange(date, startTime, endTime)
  const availabilityQuery = useQuery({
    queryKey: ['resource-availability', range],
    queryFn: () => {
      if (!range) throw new Error('The planning slot is incomplete.')
      return getResourceAvailability(range)
    },
    enabled: Boolean(range),
    staleTime: 30_000,
  })

  useEffect(() => {
    reset(defaults)
  }, [defaults, reset])

  useEffect(() => {
    onDateChange(date)
  }, [date, onDateChange])

  const teamLeaders = useMemo(() => {
    const values = [...(suggestions?.teamLeaders ?? [])]
    if (existingRound && !values.some((leader) => leader.id === existingRound.teamLeaderId)) {
      values.unshift(existingRound.teamLeader ?? { id: existingRound.teamLeaderId, name: `Team leader ${existingRound.teamLeaderId}` })
    }
    return values
  }, [existingRound, suggestions?.teamLeaders])

  const selectedRequests = selectedRequestIds.map((id) => requests.find((request) => request.id === id)).filter(Boolean) as HarvestRequest[]
  const estimatedKg = selectedRequests.reduce((total, request) => total + request.estimatedQuantityKg, 0)
  const selectedMill = mills.find((mill) => mill.id === selectedMillId)
  const capacityWarning = millCapacityWarning(estimatedKg, selectedMill)
  const existingRequestIds = existingRound?.stops.map((stop) => stop.harvestRequestId) ?? []
  const existingResourceIds = existingRound ? [...existingRound.workerIds, ...existingRound.equipmentIds] : []

  const mutation = useMutation({
    mutationFn: (values: RoundWriteValues) => {
      const payload = buildRoundPayload(values)
      return existingRound
        ? updateRound(existingRound.id, { ...payload, version: existingRound.version })
        : createRound(payload)
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['rounds'] })
      await queryClient.invalidateQueries({ queryKey: ['planning-requests'] })
      await queryClient.invalidateQueries({ queryKey: ['planning-suggestions'] })
      onSaved(result)
    },
  })

  const onSubmit: SubmitHandler<RoundFormValues> = (values) => {
    const raw: RoundWriteValues = { ...values, requestIds: selectedRequestIds }
    const errorsFromSelection = validateRoundSelection(raw, {
      requests,
      teamLeaders,
      availability: availabilityQuery.data,
      mills,
      existingRequestIds,
      existingResourceIds,
      existingTeamLeaderId: existingRound?.teamLeaderId,
    })
    if (!range) errorsFromSelection.push('Choose a valid time range with the end after the start.')
    if (range && availabilityQuery.isPending) errorsFromSelection.push('Wait for resource availability to finish loading.')
    if (availabilityQuery.isError) errorsFromSelection.push('Resource availability could not be checked. Refresh before saving.')
    setSelectionErrors(errorsFromSelection)
    if (errorsFromSelection.length > 0) return
    mutation.mutate(raw)
  }

  const mutationError = mutation.error
  const availability = availabilityQuery.data

  return (
    <Card>
      <CardHeader>
        <CardTitle>{existingRound ? `Edit round ${existingRound.code}` : 'Plan a collection round'}</CardTitle>
        <p className="text-sm text-muted-foreground">
          Selected requests remain visible while you choose a slot and resources. Saving is blocked if the server data is stale.
        </p>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field id="round-date" label="Date" type="date" error={errors.date?.message} {...register('date', { required: 'Choose a date.' })} />
            <Field id="round-start" label="Start" type="time" error={errors.startTime?.message} {...register('startTime', { required: 'Choose a start time.' })} />
            <Field id="round-end" label="End" type="time" error={errors.endTime?.message} {...register('endTime', { required: 'Choose an end time.' })} />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="round-team-leader">Team leader</Label>
            <select id="round-team-leader" className="h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm" aria-invalid={!!errors.teamLeaderId} {...register('teamLeaderId', { required: 'Choose a team leader.' })}>
              <option value="">Choose a team leader</option>
              {teamLeaders.map((leader) => (
                <option key={leader.id} value={leader.id}>
                  {teamLeaderLabel(leader)}{leader.available === false ? ' · busy' : ''}
                </option>
              ))}
            </select>
            {errors.teamLeaderId?.message && <p className="text-sm text-destructive">{errors.teamLeaderId.message}</p>}
            {teamLeaders.length === 0 && <p className="text-sm text-muted-foreground">No team leaders are available for this date.</p>}
          </div>

          <SelectedRequests requests={selectedRequests} selectedIds={selectedRequestIds} />

          <ResourceChoices
            label="Workers"
            name="workerIds"
            resources={availability?.workers ?? []}
            selectedIds={selectedWorkerIds}
            existingIds={existingResourceIds}
            register={register}
          />
          <ResourceChoices
            label="Equipment and bins"
            name="equipmentIds"
            resources={availability?.equipment ?? []}
            selectedIds={selectedEquipmentIds}
            existingIds={existingResourceIds}
            register={register}
            showCapacity
          />

          {availabilityQuery.isPending && <p className="text-sm text-muted-foreground">Checking free/busy resources…</p>}
          {availabilityQuery.isError && <p role="alert" className="text-sm text-destructive">Could not load resource availability.</p>}

          <div className="flex flex-col gap-2">
            <Label htmlFor="round-mill">Destination mill</Label>
            <select id="round-mill" className="h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm" aria-invalid={!!errors.millId} {...register('millId', { required: 'Choose a destination mill.' })}>
              <option value="">Choose a mill</option>
              {mills.map((mill) => (
                <option key={mill.id} value={mill.id}>
                  {mill.name} · {formatKg(mill.availableCapacityKg)} available
                </option>
              ))}
            </select>
            {errors.millId?.message && <p className="text-sm text-destructive">{errors.millId.message}</p>}
            {capacityWarning && <WarningBox warning={capacityWarning} />}
          </div>

          {selectionErrors.length > 0 && (
            <div role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
              <p className="font-medium">Fix these selections before saving:</p>
              <ul className="mt-1 list-disc pl-5">
                {selectionErrors.map((message) => <li key={message}>{message}</li>)}
              </ul>
            </div>
          )}
          {mutationError && <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{planningErrorMessage(mutationError)}</p>}

          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving…' : existingRound ? 'Save changes' : 'Create round'}
            </Button>
            {existingRound && onCancelEdit && <Button type="button" variant="outline" onClick={onCancelEdit} disabled={mutation.isPending}>Cancel editing</Button>}
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

function SelectedRequests({ requests, selectedIds }: { requests: HarvestRequest[]; selectedIds: string[] }) {
  const requestMap = new Map(requests.map((request) => [request.id, request]))
  return (
    <section className="flex flex-col gap-2" aria-labelledby="selected-requests-heading">
      <div className="flex items-center justify-between gap-2">
        <h2 id="selected-requests-heading" className="font-medium">Requests in this round</h2>
        <span className="text-sm text-muted-foreground">{selectedIds.length} selected</span>
      </div>
      {selectedIds.length === 0 && <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">Select validated requests from the board.</p>}
      {selectedIds.length > 0 && (
        <ul className="flex flex-col gap-2">
          {selectedIds.map((id) => {
            const request = requestMap.get(id)
            return (
              <li key={id} className="flex items-center justify-between gap-2 rounded-lg bg-muted/50 px-3 py-2 text-sm">
                <span>{request?.orchard.name ?? `Request ${id} · no longer in the validated queue`}</span>
                {request && <span className="shrink-0 text-muted-foreground">{formatKg(request.estimatedQuantityKg)}</span>}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function ResourceChoices({
  label,
  name,
  resources,
  selectedIds,
  existingIds,
  register,
  showCapacity = false,
}: {
  label: string
  name: 'workerIds' | 'equipmentIds'
  resources: ResourceAvailability[]
  selectedIds: string[]
  existingIds: string[]
  register: ReturnType<typeof useForm<RoundFormValues>>['register']
  showCapacity?: boolean
}) {
  const existing = new Set(existingIds)
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="font-medium">{label}</legend>
      {resources.length === 0 && <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">No resources returned for this slot.</p>}
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {resources.map((resource) => {
          const isBusy = !resource.available
          const canKeepExisting = existing.has(resource.id)
          return (
            <label key={resource.id} className={`flex min-h-11 items-center gap-3 rounded-lg border px-3 py-2 text-sm ${isBusy && !canKeepExisting ? 'opacity-60' : ''}`}>
              <Input
                type="checkbox"
                value={resource.id}
                className="size-5 min-h-5 w-5"
                disabled={isBusy && !canKeepExisting}
                {...register(name)}
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate">{resourceLabel(resource)}</span>
                <span className="block text-xs text-muted-foreground">
                  {showCapacity && resource.capacityKg ? `${formatKg(resource.capacityKg)} · ` : ''}{isBusy ? resource.reason ?? 'Busy' : 'Free'}
                </span>
              </span>
            </label>
          )
        })}
      </div>
      {selectedIds.length > 0 && <p className="text-xs text-muted-foreground">{selectedIds.length} selected</p>}
    </fieldset>
  )
}

function WarningBox({ warning }: { warning: { code: string; message?: string | null; detail?: string | null } }) {
  return <p className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-950">{formatRoundWarning(warning)}</p>
}

function roundDefaults(round: CollectionRound | null | undefined, defaultDate: string): RoundFormValues {
  return {
    date: round?.date ?? defaultDate,
    startTime: round?.startTime ?? '07:00',
    endTime: round?.endTime ?? '15:00',
    teamLeaderId: round?.teamLeaderId ?? '',
    workerIds: round?.workerIds ?? [],
    equipmentIds: round?.equipmentIds ?? [],
    millId: round?.millId ?? '',
  }
}
