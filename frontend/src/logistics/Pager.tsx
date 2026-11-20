import { Button } from '@/components/ui/button'
import { PAGE_SIZE } from '@/admin/users'

export function Pager({ page, total, onChange }: { page: number; total: number; onChange: (page: number) => void }) {
  const lastPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1)
  return (
    <div className="flex items-center justify-between gap-2">
      <Button variant="outline" className="h-11" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-muted-foreground">
        Page {page + 1} of {lastPage + 1}
      </span>
      <Button variant="outline" className="h-11" disabled={page >= lastPage} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </div>
  )
}
