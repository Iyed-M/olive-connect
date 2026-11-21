import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useAuth } from '@/auth/AuthContext'
import { ApiError } from '@/lib/api'
import { errorBanner, setServerErrors } from '@/admin/users'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { Pager } from './Pager'
import {
  SUPPLY_STATUS_LABEL,
  createSupplyOrder,
  listSupplyOrders,
  listTools,
  receiveSupplyOrder,
  type SupplyStatus,
} from './resources'

function banner(err: unknown) {
  return err instanceof ApiError ? errorBanner(err) : 'Could not reach the server.'
}

interface SupplyValues {
  toolId: string
  quantity: string
  supplier: string
  expectedAt: string
}

// Supply orders of nets and shakers (FR-3.5). The Logistics manager creates and receives;
// the Admin only reads.
export function SupplyOrders() {
  const { user } = useAuth()
  const canWrite = !!user?.roles.includes('LOGISTICS_MANAGER')
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<SupplyStatus | ''>('')
  const [page, setPage] = useState(0)

  const orders = useQuery({
    queryKey: ['supply-orders', status, page],
    queryFn: () => listSupplyOrders(status, page),
    placeholderData: keepPreviousData,
  })
  const nets = useQuery({ queryKey: ['tools', 'NET'], queryFn: () => listTools('NET'), enabled: canWrite })
  const shakers = useQuery({ queryKey: ['tools', 'SHAKER'], queryFn: () => listTools('SHAKER'), enabled: canWrite })

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    reset,
    formState: { errors },
  } = useForm<SupplyValues>({ defaultValues: { toolId: '', quantity: '', supplier: '', expectedAt: '' } })

  // The stock of a tool changes when an order is received: refresh the equipment pages too.
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['supply-orders'] })
    await queryClient.invalidateQueries({ queryKey: ['tools'] })
    await queryClient.invalidateQueries({ queryKey: ['equipment'] })
    await queryClient.invalidateQueries({ queryKey: ['equipment-item'] })
  }

  const create = useMutation({
    mutationFn: (v: SupplyValues) =>
      createSupplyOrder({
        toolId: v.toolId,
        quantity: Number(v.quantity),
        supplier: v.supplier.trim(),
        expectedAt: v.expectedAt || null,
      }),
    onSuccess: async () => {
      reset()
      await refresh()
    },
    onError: (err) => {
      if (err instanceof ApiError) setServerErrors(err.errors, setError)
    },
  })
  const receive = useMutation({ mutationFn: receiveSupplyOrder, onSuccess: refresh })

  const data = orders.data

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Supply orders</h1>

      {canWrite && (
        <Card>
          <CardHeader>
            <CardTitle>New supply order</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={handleSubmit((v) => {
                clearErrors()
                create.mutate(v)
              })}
              className="flex flex-col gap-4"
              noValidate
            >
              <div className="flex flex-col gap-2">
                <label htmlFor="toolId" className="text-sm font-medium">
                  Tool
                </label>
                <select
                  id="toolId"
                  className={selectClass}
                  aria-invalid={!!errors.toolId}
                  {...register('toolId', { required: 'Choose a tool.' })}
                >
                  <option value="">Choose a net or a shaker</option>
                  <optgroup label="Nets">
                    {nets.data?.content.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.code} (stock {t.stockQuantity ?? 0})
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label="Shakers">
                    {shakers.data?.content.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.code} (stock {t.stockQuantity ?? 0})
                      </option>
                    ))}
                  </optgroup>
                </select>
                {errors.toolId && <p className="text-sm text-destructive">{errors.toolId.message}</p>}
              </div>
              <Field
                id="quantity"
                label="Quantity"
                type="number"
                min="1"
                step="1"
                error={errors.quantity?.message}
                {...register('quantity', {
                  required: 'Quantity is required.',
                  validate: (v) => (Number.isInteger(Number(v)) && Number(v) >= 1) || 'Quantity must be at least 1.',
                })}
              />
              <Field
                id="supplier"
                label="Supplier"
                maxLength={120}
                error={errors.supplier?.message}
                {...register('supplier', {
                  validate: (v) => v.trim().length > 0 || 'Supplier is required.',
                })}
              />
              <Field
                id="expectedAt"
                label="Expected on (optional)"
                type="date"
                error={errors.expectedAt?.message}
                {...register('expectedAt')}
              />
              {create.isError && <p role="alert" className="text-sm text-destructive">{banner(create.error)}</p>}
              <Button type="submit" className="h-11 w-fit" disabled={create.isPending}>
                {create.isPending ? 'Saving...' : 'Create order'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      <select
        aria-label="Filter by status"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as SupplyStatus | '')
          setPage(0)
        }}
        className={selectClass + ' sm:w-60'}
      >
        <option value="">All statuses</option>
        {(Object.keys(SUPPLY_STATUS_LABEL) as SupplyStatus[]).map((s) => (
          <option key={s} value={s}>
            {SUPPLY_STATUS_LABEL[s]}
          </option>
        ))}
      </select>

      {orders.isError && <p role="alert" className="text-sm text-destructive">Could not load the supply orders.</p>}
      {receive.isError && <p role="alert" className="text-sm text-destructive">{banner(receive.error)}</p>}
      {orders.isPending && <p className="text-muted-foreground">Loading...</p>}
      {data && data.content.length === 0 && <p className="text-muted-foreground">No supply order.</p>}

      {data && data.content.length > 0 && (
        <>
          <ul className="flex flex-col gap-2">
            {data.content.map((o) => (
              <li key={o.id}>
                <Card>
                  <CardContent className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-col">
                      <span className="font-medium">
                        {o.quantity} x {o.toolCode ?? o.toolId}
                      </span>
                      <span className="text-sm text-muted-foreground">
                        {o.supplier} · ordered {o.orderedAt}
                        {o.expectedAt && ` · expected ${o.expectedAt}`}
                        {o.receivedAt && ` · received ${o.receivedAt}`}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={o.status === 'RECEIVED' ? 'default' : 'outline'}>{SUPPLY_STATUS_LABEL[o.status]}</Badge>
                      {canWrite && o.status === 'ORDERED' && (
                        <Button
                          variant="outline"
                          className="h-11"
                          disabled={receive.isPending}
                          onClick={() => receive.mutate(o.id)}
                        >
                          Mark received
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>
          <Pager page={page} total={data.totalElements} onChange={setPage} />
        </>
      )}
    </>
  )
}
