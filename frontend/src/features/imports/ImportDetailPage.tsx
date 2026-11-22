import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { downloadErrorsCsv, getImport } from './imports'
import { ImportStatusBadge } from './ImportStatusBadge'

export function ImportDetailPage({ id }: { id: string }) {
  const query = useQuery({ queryKey: ['import', id], queryFn: () => getImport(id) })
  const job = query.data
  const errors = job?.errors ?? []
  const total = job?.errorCount ?? errors.length

  return (
    <>
      <Link to="/intranet/imports" className="text-sm text-primary hover:underline">
        Retour au journal
      </Link>
      {query.isPending && <p className="text-muted-foreground">Chargement…</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Impossible de charger cet import.
        </p>
      )}
      {job && (
        <>
          <div className="flex items-center justify-between gap-2">
            <h1 className="break-all font-heading text-2xl font-bold text-primary">{job.fileName}</h1>
            <ImportStatusBadge status={job.status} />
          </div>
          <Card>
            <CardContent className="grid grid-cols-2 gap-2 text-sm">
              <span className="text-muted-foreground">Jeu de données</span>
              <span>{job.dataset}</span>
              <span className="text-muted-foreground">Source</span>
              <span>{job.source}</span>
              <span className="text-muted-foreground">Démarré</span>
              <span>{new Date(job.startedAt).toLocaleString()}</span>
              <span className="text-muted-foreground">Terminé</span>
              <span>{job.finishedAt ? new Date(job.finishedAt).toLocaleString() : '-'}</span>
              <span className="text-muted-foreground">Lignes</span>
              <span>{job.totalRows}</span>
              <span className="text-muted-foreground">Insérées / mises à jour</span>
              <span>
                {job.insertedRows} / {job.updatedRows}
              </span>
              <span className="text-muted-foreground">Rejetées</span>
              <span>{job.rejectedRows}</span>
            </CardContent>
          </Card>

          <div className="flex items-center justify-between gap-2">
            <h2 className="font-heading text-lg font-semibold">Lignes rejetées ({total})</h2>
            {errors.length > 0 && (
              <Button variant="outline" className="h-11" onClick={() => downloadErrorsCsv(job)}>
                <Download aria-hidden /> Télécharger le CSV
              </Button>
            )}
          </div>
          {errors.length === 0 && <p className="text-muted-foreground">Aucune ligne rejetée.</p>}
          {total > errors.length && (
            <p className="text-sm text-muted-foreground">Seules les {errors.length} premières lignes rejetées sont conservées.</p>
          )}
          {errors.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-2">Ligne</th>
                    <th className="pr-2">Champ</th>
                    <th>Motif</th>
                  </tr>
                </thead>
                <tbody>
                  {errors.map((e, i) => (
                    <tr key={i} className="border-b align-top">
                      <td className="py-2 pr-2">{e.line}</td>
                      <td className="pr-2">{e.field}</td>
                      <td>{e.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </>
  )
}
