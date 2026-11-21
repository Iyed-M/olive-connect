import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { selectClass } from '@/components/Field'
import {
  addDays,
  daySlots,
  getResourceCalendar,
  listEquipment,
  listWorkers,
  startOfWeek,
} from './resources'

// Day or week calendar of one worker or one equipment (FR-3.3): bookings of rounds and unavailabilities.
export function ResourceCalendar() {
  const [resourceId, setResourceId] = useState('')
  const [view, setView] = useState<'day' | 'week'>('week')
  // First displayed day: a Monday in week view, any day in day view.
  const [start, setStart] = useState(() => startOfWeek(new Date()))
  const span = view === 'week' ? 7 : 1

  function changeView(next: 'day' | 'week') {
    setView(next)
    if (next === 'week') setStart(startOfWeek(start))
  }
  function today() {
    setStart(view === 'week' ? startOfWeek(new Date()) : new Date(new Date().toDateString()))
  }

  const workers = useQuery({
    queryKey: ['workers', 'all'],
    queryFn: () => listWorkers({ active: '', page: 0 }),
  })
  const equipment = useQuery({
    queryKey: ['equipment', 'all'],
    queryFn: () => listEquipment({ type: '', status: '', page: 0 }),
  })

  const end = addDays(start, span)
  const calendar = useQuery({
    queryKey: ['calendar', resourceId, start.toISOString(), span],
    queryFn: () => getResourceCalendar(resourceId, start.toISOString(), end.toISOString()),
    enabled: resourceId !== '',
  })

  const days = Array.from({ length: span }, (_, i) => addDays(start, i))

  return (
    <>
      <h1 className="font-heading text-2xl font-bold text-primary">Resource calendar</h1>

      <select
        aria-label="Resource"
        value={resourceId}
        onChange={(e) => setResourceId(e.target.value)}
        className={selectClass}
      >
        <option value="">Choose a worker or an equipment</option>
        <optgroup label="Workers">
          {workers.data?.content.map((w) => (
            <option key={w.id} value={w.id}>
              {w.firstName} {w.lastName}
            </option>
          ))}
        </optgroup>
        <optgroup label="Equipment">
          {equipment.data?.content.map((e) => (
            <option key={e.id} value={e.id}>
              {e.code}
            </option>
          ))}
        </optgroup>
      </select>
      <p className="text-xs text-muted-foreground">The lists show the first 20 resources of each kind.</p>

      <div className="flex gap-2">
        {(['day', 'week'] as const).map((v) => (
          <Button
            key={v}
            type="button"
            variant={view === v ? 'default' : 'outline'}
            className="h-11"
            aria-pressed={view === v}
            onClick={() => changeView(v)}
          >
            {v === 'day' ? 'Day' : 'Week'}
          </Button>
        ))}
      </div>

      <div className="flex items-center justify-between gap-2">
        <Button variant="outline" className="h-11" aria-label={`Previous ${view}`} onClick={() => setStart(addDays(start, -span))}>
          <ChevronLeft aria-hidden />
        </Button>
        <div className="flex flex-col items-center">
          <span className="font-medium">
            {view === 'week'
              ? `${start.toLocaleDateString()} - ${addDays(start, 6).toLocaleDateString()}`
              : start.toLocaleDateString()}
          </span>
          <button type="button" className="text-sm text-primary hover:underline" onClick={today}>
            {view === 'week' ? 'This week' : 'Today'}
          </button>
        </div>
        <Button variant="outline" className="h-11" aria-label={`Next ${view}`} onClick={() => setStart(addDays(start, span))}>
          <ChevronRight aria-hidden />
        </Button>
      </div>

      {resourceId === '' && <p className="text-muted-foreground">Choose a resource to see its calendar.</p>}
      {calendar.isPending && resourceId !== '' && <p className="text-muted-foreground">Loading...</p>}
      {calendar.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the calendar.
        </p>
      )}

      {calendar.data && (
        <>
          <p className="text-sm text-muted-foreground">{calendar.data.label}</p>
          <div className={view === 'week' ? 'grid grid-cols-1 gap-2 md:grid-cols-7' : 'grid grid-cols-1 gap-2'}>
            {days.map((day) => {
              const slots = daySlots(day, calendar.data)
              return (
                <section key={day.toISOString()} className="flex min-h-24 flex-col gap-1 rounded-lg border p-2">
                  <h2 className="text-sm font-medium">
                    {day.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'numeric' })}
                  </h2>
                  {slots.length === 0 && <p className="text-xs text-muted-foreground">Free</p>}
                  {slots.map((s, i) => (
                    <div
                      key={i}
                      className={
                        s.kind === 'booking'
                          ? 'rounded bg-primary/10 p-1 text-xs'
                          : 'rounded bg-destructive/10 p-1 text-xs'
                      }
                    >
                      <div className="font-medium">{s.label}</div>
                      <div>{s.kind === 'booking' ? 'Booked by a round' : s.text}</div>
                    </div>
                  ))}
                </section>
              )
            })}
          </div>
        </>
      )}
    </>
  )
}
