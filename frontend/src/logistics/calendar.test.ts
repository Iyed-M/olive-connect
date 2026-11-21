import { describe, expect, it } from 'vitest'
import { addDays, daySlots, startOfWeek, type ResourceCalendarDto } from './resources'

describe('calendar helpers', () => {
  it('starts the week on Monday', () => {
    const monday = startOfWeek(new Date(2026, 10, 22)) // Sunday 22 Nov 2026
    expect(monday.getDay()).toBe(1)
    expect(monday.getDate()).toBe(16)
  })

  it('clips a multi-day unavailability to each day and ignores other days', () => {
    const day = new Date(2026, 10, 16)
    const cal: ResourceCalendarDto = {
      resourceId: 'r',
      resourceKind: 'WORKER',
      label: 'x',
      bookings: [{ id: 'b', roundId: 'rd', start: new Date(2026, 10, 16, 8).toISOString(), end: new Date(2026, 10, 16, 12).toISOString() }],
      unavailabilities: [{ from: new Date(2026, 10, 16, 20).toISOString(), to: new Date(2026, 10, 17, 6).toISOString(), reason: 'Leave' }],
    }
    const slots = daySlots(day, cal)
    expect(slots.map((s) => s.kind)).toEqual(['booking', 'unavailable'])
    expect(slots[1].label).toBe('20:00 - 24:00')
    expect(daySlots(addDays(day, 1), cal)).toHaveLength(1)
    expect(daySlots(addDays(day, 2), cal)).toHaveLength(0)
  })
})
