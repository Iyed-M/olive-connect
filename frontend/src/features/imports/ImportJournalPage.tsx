import { useRef, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { selectClass } from '@/components/Field'
import { ApiError } from '@/lib/api'
import { Pager } from '@/logistics/Pager'
import {
  listImports,
  runImport,
  uploadImport,
  type ImportFilters,
  type ImportRunType,
  type ImportStatus,
} from './imports'
import { ImportStatusBadge } from './ImportStatusBadge'

const STATUSES: ImportStatus[] = ['RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED']

const formatDate = (iso: string) => new Date(iso).toLocaleString()
const errorText = (err: unknown) => (err instanceof ApiError ? err.detail : 'Cannot reach the server.')

export function ImportJournalPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<ImportFilters>({ dataset: '', status: '', page: 0 })
  const fileInput = useRef<HTMLInputElement>(null)
  const query = useQuery({
    queryKey: ['imports', filters],
    queryFn: () => listImports(filters),
    placeholderData: keepPreviousData,
  })
  const data = query.data

  const run = useMutation({
    mutationFn: (type: ImportRunType) => runImport(type),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['imports'] }),
  })
  const upload = useMutation({
    mutationFn: (file: File) => uploadImport(file),
    onSuccess: () => {
      if (fileInput.current) fileInput.current.value = ''
      return queryClient.invalidateQueries({ queryKey: ['imports'] })
    },
  })

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Import journal</h1>

      <Card>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">Run an import now or upload a file into the inbox.</p>
          <div className="flex flex-wrap gap-2">
            <Button className="h-11" disabled={run.isPending} onClick={() => run.mutate('REFERENCE')}>
              Run reference data
            </Button>
            <Button className="h-11" variant="outline" disabled={run.isPending} onClick={() => run.mutate('DYNAMIC')}>
              Run dynamic data
            </Button>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              ref={fileInput}
              type="file"
              accept=".csv,.json"
              aria-label="File to upload"
              className="h-11"
            />
            <Button
              className="h-11"
              disabled={upload.isPending}
              onClick={() => {
                const file = fileInput.current?.files?.[0]
                if (file) upload.mutate(file)
              }}
            >
              {upload.isPending ? 'Uploading...' : 'Upload'}
            </Button>
          </div>
          {run.isSuccess && (
            <p role="status" className="text-sm text-primary">
              Import started.
            </p>
          )}
          {upload.isSuccess && (
            <p role="status" className="text-sm text-primary">
              File uploaded.
            </p>
          )}
          {(run.error || upload.error) && (
            <p role="alert" className="text-sm text-destructive">
              {errorText(run.error ?? upload.error)}
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <Input
          aria-label="Filter by dataset"
          placeholder="Dataset (e.g. WEATHER)"
          className="h-11"
          value={filters.dataset}
          onChange={(e) => setFilters({ ...filters, dataset: e.target.value, page: 0 })}
        />
        <select
          aria-label="Filter by status"
          value={filters.status}
          onChange={(e) => setFilters({ ...filters, status: e.target.value as ImportFilters['status'], page: 0 })}
          className={selectClass}
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      {query.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the journal.
        </p>
      )}
      {query.isPending && <p className="text-muted-foreground">Loading...</p>}
      {data && data.content.length === 0 && <p className="text-muted-foreground">No import found.</p>}

      {data && data.content.length > 0 && (
        <>
          <ul className="flex flex-col gap-2">
            {data.content.map((job) => (
              <li key={job.id}>
                <Link to="/intranet/imports/$id" params={{ id: job.id }}>
                  <Card>
                    <CardContent className="flex flex-col gap-1">
                      <div className="flex items-center justify-between gap-2">
                        <span className="break-all font-medium">{job.fileName}</span>
                        <ImportStatusBadge status={job.status} />
                      </div>
                      <div className="flex flex-wrap gap-2 text-sm text-muted-foreground">
                        <Badge variant="secondary">{job.dataset}</Badge>
                        <span>{formatDate(job.startedAt)}</span>
                      </div>
                      <span className="text-sm text-muted-foreground">
                        {job.totalRows} rows: {job.insertedRows} inserted, {job.updatedRows} updated,{' '}
                        {job.rejectedRows} rejected
                      </span>
                    </CardContent>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>
          <Pager
            page={filters.page}
            total={data.totalElements}
            onChange={(page) => setFilters({ ...filters, page })}
          />
        </>
      )}
    </>
  )
}
