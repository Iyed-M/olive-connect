import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { ApiError } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field, selectClass } from '@/components/Field'
import { CorrectionHistory } from '@/components/CorrectionHistory'
import {
  ROUND_STATUS_LABEL,
  correctRound,
  listRounds,
  type CollectionRound,
  type RoundStatus,
} from '@/features/collection/planning'

interface CorrectionValues {
  totalHarvestedKg: string
  comment: string
}

// Intranet grid of rounds. A COMPLETED round can get a corrected harvested weight (FR-6.5);
// every correction is kept with its comment in the round history.
export function RoundsGrid() {
  const [status, setStatus] = useState<RoundStatus | ''>('COMPLETED')
  const [editing, setEditing] = useState<string | null>(null)
  const query = useQuery({
    queryKey: ['rounds', 'grid', status],
    queryFn: () => listRounds(status ? { status } : {}),
  })
  const rounds = query.data?.content ?? []

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Tournées</h1>
      <select
        aria-label="Filtrer par statut"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as RoundStatus | '')
          setEditing(null)
        }}
        className={selectClass + ' sm:w-60'}
      >
        <option value="">Tous les statuts</option>
        {(Object.keys(ROUND_STATUS_LABEL) as RoundStatus[]).map((s) => (
          <option key={s} value={s}>
            {ROUND_STATUS_LABEL[s]}
          </option>
        ))}
      </select>
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {query.isError && <p role="alert" className="text-sm text-destructive">Impossible de charger les tournées.</p>}
      {query.data && rounds.length === 0 && <p className="text-muted-foreground">Aucune tournée trouvée.</p>}
      <ul className="flex flex-col gap-2">
        {rounds.map((r) => (
          <li key={r.id}>
            <Card>
              <CardContent className="flex flex-col gap-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-col">
                    <span className="font-medium">
                      {r.code} · {r.date}
                    </span>
                    <span className="text-sm text-muted-foreground">
                      Estimé {r.estimatedTotalKg} kg · récolté {r.totalHarvestedKg} kg
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{ROUND_STATUS_LABEL[r.status]}</Badge>
                    {r.status === 'COMPLETED' && (
                      <Button variant="outline" className="h-11" onClick={() => setEditing(editing === r.id ? null : r.id)}>
                        {editing === r.id ? 'Fermer' : 'Corriger'}
                      </Button>
                    )}
                  </div>
                </div>
                {editing === r.id && <CorrectionForm round={r} onDone={() => setEditing(null)} />}
                <CorrectionHistory items={r.corrections} />
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
    </>
  )
}

function CorrectionForm({ round, onDone }: { round: CollectionRound; onDone: () => void }) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CorrectionValues>({ defaultValues: { totalHarvestedKg: String(round.totalHarvestedKg), comment: '' } })

  const save = useMutation({
    mutationFn: (v: CorrectionValues) =>
      correctRound(round.id, { totalHarvestedKg: Number(v.totalHarvestedKg), comment: v.comment.trim() }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['rounds'] })
      onDone()
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        for (const { field, message } of err.errors) {
          if (field === 'totalHarvestedKg' || field === 'comment') setError(field, { type: 'server', message })
        }
      }
    },
  })

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} className="flex flex-col gap-3 rounded-lg border p-3" noValidate>
      <Field
        id="totalHarvestedKg"
        label="Poids récolté (kg)"
        type="number"
        min="0"
        step="0.1"
        error={errors.totalHarvestedKg?.message}
        {...register('totalHarvestedKg', {
          required: 'Le poids est obligatoire.',
          validate: (v) => Number(v) >= 0 || 'Le poids ne peut pas être négatif.',
        })}
      />
      <Field
        id="comment"
        label="Motif de la correction"
        maxLength={500}
        error={errors.comment?.message}
        {...register('comment', { validate: (v) => v.trim().length > 0 || 'Le motif est obligatoire.' })}
      />
      {save.isError && (
        <p role="alert" className="text-sm text-destructive">
          {save.error instanceof ApiError ? save.error.detail : 'Impossible d’enregistrer la correction.'}
        </p>
      )}
      <Button type="submit" className="h-11 w-fit" disabled={save.isPending}>
        {save.isPending ? 'Enregistrement…' : 'Enregistrer la correction'}
      </Button>
    </form>
  )
}
