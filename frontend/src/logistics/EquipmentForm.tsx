import { useState, type FormEvent } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, fieldMessages } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Field, selectClass } from './Field'
import {
  EQUIPMENT_STATUS_LABEL,
  EQUIPMENT_TYPE_LABEL,
  createEquipment,
  deleteEquipment,
  parseNumber,
  updateEquipment,
  type EquipmentDto,
  type EquipmentStatus,
  type EquipmentType,
  type EquipmentWriteBody,
} from './resources'

// One form for "new" (no `equipment`) and "edit". The kind-specific fields follow the type.
// The type cannot change on an existing equipment (the server answers 422), so it is read-only on edit.
export function EquipmentForm({ equipment }: { equipment?: EquipmentDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [type, setType] = useState<EquipmentType>(equipment?.type ?? 'TRACTOR')
  const [code, setCode] = useState(equipment?.code ?? '')
  const [status, setStatus] = useState<EquipmentStatus>(equipment?.status ?? 'AVAILABLE')
  const [plateNumber, setPlateNumber] = useState(equipment?.plateNumber ?? '')
  const [powerHp, setPowerHp] = useState(equipment?.powerHp != null ? String(equipment.powerHp) : '')
  const [serialNumber, setSerialNumber] = useState(equipment?.serialNumber ?? '')
  const [capacityKg, setCapacityKg] = useState(equipment?.capacityKg != null ? String(equipment.capacityKg) : '')
  const [stockQuantity, setStockQuantity] = useState(equipment?.stockQuantity != null ? String(equipment.stockQuantity) : '')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [saved, setSaved] = useState(false)

  const isTool = type === 'NET' || type === 'SHAKER'

  const save = useMutation({
    mutationFn: () => {
      const body: EquipmentWriteBody = {
        code,
        type,
        status,
        plateNumber: type === 'TRACTOR' ? plateNumber : null,
        powerHp: type === 'TRACTOR' ? parseNumber(powerHp) : null,
        serialNumber: type === 'BIN' ? serialNumber : null,
        capacityKg: type === 'BIN' ? parseNumber(capacityKg) : null,
        toolType: isTool ? type : null,
        stockQuantity: isTool ? parseNumber(stockQuantity) : null,
      }
      return equipment ? updateEquipment(equipment.id, body) : createEquipment(body)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['equipment'] })
      if (equipment) {
        await queryClient.invalidateQueries({ queryKey: ['equipment-item', equipment.id] })
        setSaved(true)
      } else {
        await navigate({ to: '/logistics/equipment' })
      }
    },
  })

  const remove = useMutation({
    mutationFn: () => deleteEquipment(equipment!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['equipment'] })
      await navigate({ to: '/logistics/equipment' })
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSaved(false)
    save.mutate()
  }

  const error = save.error instanceof ApiError ? save.error : null
  const removeError = remove.error instanceof ApiError ? remove.error : null
  const fields = error ? fieldMessages(error.errors) : {}
  const banner = save.error ? (error ? errorBanner(error) : 'Cannot reach the server.') : null

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="type">Type</Label>
            <select
              id="type"
              value={type}
              disabled={!!equipment}
              onChange={(e) => setType(e.target.value as EquipmentType)}
              className={selectClass}
            >
              {(Object.keys(EQUIPMENT_TYPE_LABEL) as EquipmentType[]).map((t) => (
                <option key={t} value={t}>
                  {EQUIPMENT_TYPE_LABEL[t]}
                </option>
              ))}
            </select>
            {fields.type && <p className="text-sm text-destructive">{fields.type}</p>}
          </div>
          <Field id="code" label="Code" value={code} onChange={setCode} error={fields.code} />
          <div className="flex flex-col gap-2">
            <Label htmlFor="status">Status</Label>
            <select id="status" value={status} onChange={(e) => setStatus(e.target.value as EquipmentStatus)} className={selectClass}>
              {(Object.keys(EQUIPMENT_STATUS_LABEL) as EquipmentStatus[]).map((s) => (
                <option key={s} value={s}>
                  {EQUIPMENT_STATUS_LABEL[s]}
                </option>
              ))}
            </select>
            {fields.status && <p className="text-sm text-destructive">{fields.status}</p>}
          </div>

          {type === 'TRACTOR' && (
            <>
              <Field id="plateNumber" label="Plate number" value={plateNumber} onChange={setPlateNumber} error={fields.plateNumber} />
              <Field id="powerHp" label="Power (hp)" type="number" value={powerHp} onChange={setPowerHp} error={fields.powerHp} />
            </>
          )}
          {type === 'BIN' && (
            <>
              <Field id="serialNumber" label="Serial number" value={serialNumber} onChange={setSerialNumber} error={fields.serialNumber} />
              <Field id="capacityKg" label="Capacity (kg)" type="number" value={capacityKg} onChange={setCapacityKg} error={fields.capacityKg} />
            </>
          )}
          {isTool && (
            <>
              <p className="text-sm text-muted-foreground">Tool type: {EQUIPMENT_TYPE_LABEL[type]}</p>
              <Field id="stockQuantity" label="Stock quantity" type="number" value={stockQuantity} onChange={setStockQuantity} error={fields.stockQuantity} />
            </>
          )}
        </CardContent>
      </Card>

      {banner && (
        <p role="alert" className="text-sm text-destructive">
          {banner}
        </p>
      )}
      {saved && !save.isPending && !save.error && (
        <p role="status" className="text-sm text-primary">
          Saved.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11" disabled={save.isPending}>
          {save.isPending ? 'Saving...' : equipment ? 'Save' : 'Create equipment'}
        </Button>
        <Button type="button" variant="outline" className="h-11" onClick={() => void navigate({ to: '/logistics/equipment' })}>
          Back to list
        </Button>
        {equipment && !confirmDelete && (
          <Button type="button" variant="destructive" className="h-11" onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        )}
      </div>
      {equipment && confirmDelete && (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Delete this equipment? This cannot be undone.</p>
          <div className="flex gap-2">
            <Button type="button" variant="destructive" className="h-11" disabled={remove.isPending} onClick={() => remove.mutate()}>
              Yes, delete
            </Button>
            <Button type="button" variant="outline" className="h-11" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {remove.error && (
        <p role="alert" className="text-sm text-destructive">
          {removeError ? errorBanner(removeError) : 'Cannot reach the server.'}
        </p>
      )}
    </form>
  )
}
