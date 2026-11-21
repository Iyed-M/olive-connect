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

// Week calendar of one worker or one equipment (FR-3.3): bookings of rounds and unavailabilities.
export function ResourceCalendar() {
  const [resourceId, setResourceId] = useState('')
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))

  const workers = useQuery({
    queryKey: ['workers', 'all'],
    queryFn: () => listWorkers({ active: '', page: 0 }),
  })
  const equipment = useQuery({
    queryKey: ['equipment', 'all'],
    queryFn: () => listEquipment({ type: '', status: '', page: 0 }),
  })

  const weekEnd = addDays(weekStart, 7)
  const calendar = useQuery({
    queryKey: ['calendar', resourceId, weekStart.toISOString()],
    queryFn: () => getResourceCalendar(resourceId, weekStart.toISOString(), weekEnd.toISOString()),
    enabled: resourceId !== '',
  })

  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))

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

      <div className="flex items-center justify-between gap-2">
        <Button variant="outline" className="h-11" aria-label="Previous week" onClick={() => setWeekStart(addDays(weekStart, -7))}>
          <ChevronLeft aria-hidden />
        </Button>
        <div className="flex flex-col items-center">
          <span className="font-medium">
            {weekStart.toLocaleDateString()} - {addDays(weekStart, 6).toLocaleDateString()}
          </span>
          <button type="button" className="text-sm text-primary hover:underline" onClick={() => setWeekStart(startOfWeek(new Date()))}>
            This week
          </button>
        </div>
        <Button variant="outline" className="h-11" aria-label="Next week" onClick={() => setWeekStart(addDays(weekStart, 7))}>
          <ChevronRight aria-hidden />
        </Button>
      </div>

      {resourceId === '' && <p className="text-muted-foreground">Choose a resource to see its week.</p>}
      {calendar.isPending && resourceId !== '' && <p className="text-muted-foreground">Loading...</p>}
      {calendar.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the calendar.
        </p>
      )}

      {calendar.data && (
        <>
          <p className="text-sm text-muted-foreground">{calendar.data.label}</p>
          <div className="grid grid-cols-1 gap-2 md:grid-cols-7">
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
