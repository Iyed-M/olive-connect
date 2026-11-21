import type { Correction } from '@/logistics/resources'

// Staff corrections of a document (FR-6.5), oldest first. Shows nothing when there is none.
export function CorrectionHistory({ items }: { items?: Correction[] }) {
  if (!items || items.length === 0) return null
  return (
    <section className="flex flex-col gap-2">
      <h2 className="font-heading text-lg font-semibold">Correction history</h2>
      <ul className="flex flex-col gap-1 text-sm">
        {items.map((c, i) => (
          <li key={i} className="rounded-lg border p-2">
            <span className="text-muted-foreground">{new Date(c.at).toLocaleString()}</span> · {c.comment}
          </li>
        ))}
      </ul>
    </section>
  )
}
