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
        Back to the journal
      </Link>
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load this import.
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
              <span className="text-muted-foreground">Dataset</span>
              <span>{job.dataset}</span>
              <span className="text-muted-foreground">Source</span>
              <span>{job.source}</span>
              <span className="text-muted-foreground">Started</span>
              <span>{new Date(job.startedAt).toLocaleString()}</span>
              <span className="text-muted-foreground">Finished</span>
              <span>{job.finishedAt ? new Date(job.finishedAt).toLocaleString() : '-'}</span>
              <span className="text-muted-foreground">Rows</span>
              <span>{job.totalRows}</span>
              <span className="text-muted-foreground">Inserted / updated</span>
              <span>
                {job.insertedRows} / {job.updatedRows}
              </span>
              <span className="text-muted-foreground">Rejected</span>
              <span>{job.rejectedRows}</span>
            </CardContent>
          </Card>

          <div className="flex items-center justify-between gap-2">
            <h2 className="font-heading text-lg font-semibold">Rejected rows ({total})</h2>
            {errors.length > 0 && (
              <Button variant="outline" className="h-11" onClick={() => downloadErrorsCsv(job)}>
                <Download aria-hidden /> Download CSV
              </Button>
            )}
          </div>
          {errors.length === 0 && <p className="text-muted-foreground">No rejected row.</p>}
          {total > errors.length && (
            <p className="text-sm text-muted-foreground">Only the first {errors.length} rejected rows are kept.</p>
          )}
          {errors.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-2">Line</th>
                    <th className="pr-2">Field</th>
                    <th>Reason</th>
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
