import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// A labelled text input with its server error. `id` is also the field name of the API.
export function Field({
  id,
  label,
  value,
  onChange,
  error,
  type,
  hint,
}: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  error?: string
  type?: string
  hint?: string
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type={type}
        autoComplete="off"
        inputMode={type === 'number' ? 'decimal' : undefined}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={!!error}
      />
      {hint && !error && <p className="text-sm text-muted-foreground">{hint}</p>}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}

export const selectClass = 'h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm'
