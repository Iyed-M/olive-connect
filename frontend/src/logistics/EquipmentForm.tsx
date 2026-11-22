import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { errorBanner, setServerErrors } from '@/admin/users'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Field, selectClass } from '@/components/Field'
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

// The values of the inputs. Numbers stay text until the request body is built.
interface EquipmentValues {
  type: EquipmentType
  code: string
  status: EquipmentStatus
  plateNumber: string
  powerHp: string
  serialNumber: string
  capacityKg: string
  stockQuantity: string
}

const numberText = (n: number | null | undefined) => (n != null ? String(n) : '')

// One form for "new" (no `equipment`) and "edit". The kind-specific fields follow the type.
// The type cannot change on an existing equipment (the server answers 422), so it is read-only on edit.
export function EquipmentForm({ equipment }: { equipment?: EquipmentDto }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<EquipmentValues>({
    defaultValues: {
      type: equipment?.type ?? 'TRACTOR',
      code: equipment?.code ?? '',
      status: equipment?.status ?? 'AVAILABLE',
      plateNumber: equipment?.plateNumber ?? '',
      powerHp: numberText(equipment?.powerHp),
      serialNumber: equipment?.serialNumber ?? '',
      capacityKg: numberText(equipment?.capacityKg),
      stockQuantity: numberText(equipment?.stockQuantity),
    },
  })
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [saved, setSaved] = useState(false)

  const type = useWatch({ control, name: 'type' })
  const isTool = type === 'NET' || type === 'SHAKER'

  const save = useMutation({
    mutationFn: (v: EquipmentValues) => {
      // A disabled select gives no value: on edit the type is the one of the equipment.
      const kind = equipment?.type ?? v.type
      const body: EquipmentWriteBody = {
        code: v.code,
        type: kind,
        status: v.status,
        plateNumber: kind === 'TRACTOR' ? v.plateNumber : null,
        powerHp: kind === 'TRACTOR' ? parseNumber(v.powerHp) : null,
        serialNumber: kind === 'BIN' ? v.serialNumber : null,
        capacityKg: kind === 'BIN' ? parseNumber(v.capacityKg) : null,
        stockQuantity: kind === 'NET' || kind === 'SHAKER' ? parseNumber(v.stockQuantity) : null,
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
    // 400: each message goes on its field. 409 and 422: the banner below.
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })

  const remove = useMutation({
    mutationFn: () => deleteEquipment(equipment!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['equipment'] })
      await navigate({ to: '/logistics/equipment' })
    },
  })

  const onSubmit = handleSubmit((values) => {
    setSaved(false)
    clearErrors()
    save.mutate(values)
  })

  const error = save.error instanceof ApiError ? save.error : null
  const removeError = remove.error instanceof ApiError ? remove.error : null
  const banner = save.error ? (error ? errorBanner(error) : 'Impossible de joindre le serveur.') : null

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <Card>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="type">Type</Label>
            <select
              id="type"
              disabled={!!equipment}
              aria-invalid={!!errors.type}
              className={selectClass}
              {...register('type')}
            >
              {(Object.keys(EQUIPMENT_TYPE_LABEL) as EquipmentType[]).map((t) => (
                <option key={t} value={t}>
                  {EQUIPMENT_TYPE_LABEL[t]}
                </option>
              ))}
            </select>
            {errors.type && <p className="text-sm text-destructive">{errors.type.message}</p>}
          </div>
          <Field id="code" label="Code" error={errors.code?.message} {...register('code')} />
          <div className="flex flex-col gap-2">
            <Label htmlFor="status">Statut</Label>
            <select id="status" aria-invalid={!!errors.status} className={selectClass} {...register('status')}>
              {(Object.keys(EQUIPMENT_STATUS_LABEL) as EquipmentStatus[]).map((s) => (
                <option key={s} value={s}>
                  {EQUIPMENT_STATUS_LABEL[s]}
                </option>
              ))}
            </select>
            {errors.status && <p className="text-sm text-destructive">{errors.status.message}</p>}
          </div>

          {type === 'TRACTOR' && (
            <>
              <Field id="plateNumber" label="Plaque d’immatriculation" error={errors.plateNumber?.message} {...register('plateNumber')} />
              <Field id="powerHp" label="Puissance (ch)" type="number" error={errors.powerHp?.message} {...register('powerHp')} />
            </>
          )}
          {type === 'BIN' && (
            <>
              <Field id="serialNumber" label="Numéro de série" error={errors.serialNumber?.message} {...register('serialNumber')} />
              <Field id="capacityKg" label="Capacité (kg)" type="number" error={errors.capacityKg?.message} {...register('capacityKg')} />
            </>
          )}
          {isTool && (
            <>
              <p className="text-sm text-muted-foreground">Tool type: {EQUIPMENT_TYPE_LABEL[type]}</p>
              <Field id="stockQuantity" label="Quantité en stock" type="number" error={errors.stockQuantity?.message} {...register('stockQuantity')} />
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
          Enregistré.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11" disabled={save.isPending}>
          {save.isPending ? 'Enregistrement…' : equipment ? 'Enregistrer' : 'Créer l’équipement'}
        </Button>
        <Button type="button" variant="outline" className="h-11" onClick={() => void navigate({ to: '/logistics/equipment' })}>
          Retour à la liste
        </Button>
        {equipment && !confirmDelete && (
          <Button type="button" variant="destructive" className="h-11" onClick={() => setConfirmDelete(true)}>
            Supprimer
          </Button>
        )}
      </div>
      {equipment && confirmDelete && (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Supprimer cet équipement ? Cette action est irréversible.</p>
          <div className="flex gap-2">
            <Button type="button" variant="destructive" className="h-11" disabled={remove.isPending} onClick={() => remove.mutate()}>
              Oui, supprimer
            </Button>
            <Button type="button" variant="outline" className="h-11" onClick={() => setConfirmDelete(false)}>
              Annuler
            </Button>
          </div>
        </div>
      )}
      {remove.error && (
        <p role="alert" className="text-sm text-destructive">
          {removeError ? errorBanner(removeError) : 'Impossible de joindre le serveur.'}
        </p>
      )}
    </form>
  )
}
