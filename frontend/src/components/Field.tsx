import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// A labelled text input with its error (client rule or server). Spread `register('name', rules)`
// on it; `id` must equal the field name, which is also the field name of the API.
export function Field({
  id,
  label,
  error,
  hint,
  type,
  ...inputProps
}: { id: string; label: string; error?: string; hint?: string } & Omit<ComponentProps<typeof Input>, 'id'>) {
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type={type}
        autoComplete="off"
        inputMode={type === 'number' ? 'decimal' : undefined}
        aria-invalid={!!error}
        {...inputProps}
      />
      {hint && !error && <p className="text-sm text-muted-foreground">{hint}</p>}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}

export const selectClass = 'h-11 w-full rounded-lg border border-input bg-background px-2 text-base md:text-sm'
