import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import type { TourStep } from '@/tours'
import { Button } from '@/components/ui/button'

interface GuidedTourProps {
  open: boolean
  steps: readonly TourStep[]
  onComplete: () => void
}

interface TargetRect {
  top: number
  left: number
  width: number
  height: number
}

interface TooltipPosition {
  top: number
  left: number
}

function positionTooltip(rect: TargetRect): TooltipPosition {
  const tooltipWidth = Math.min(320, window.innerWidth - 32)
  const left = Math.max(16, Math.min(rect.left, window.innerWidth - tooltipWidth - 16))
  const hasRoomBelow = window.innerHeight - rect.top - rect.height >= 240
  const top = hasRoomBelow ? rect.top + rect.height + 16 : Math.max(16, rect.top - 224)
  return { top, left }
}

function findVisibleTarget(selector: string): Element | null {
  return Array.from(document.querySelectorAll(selector)).find((element) => {
    const rect = element.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0
  }) ?? null
}

export function GuidedTour({ open, steps, onComplete }: GuidedTourProps) {
  const [stepIndex, setStepIndex] = useState(0)
  const [targetRect, setTargetRect] = useState<TargetRect | null>(null)
  const [tooltipPosition, setTooltipPosition] = useState<TooltipPosition | null>(null)
  const nextButtonRef = useRef<HTMLButtonElement>(null)
  const currentStep = steps[stepIndex]

  useEffect(() => {
    if (!open || !currentStep || typeof document === 'undefined') return

    const updatePosition = () => {
      const target = findVisibleTarget(currentStep.target)
      if (!target) {
        if (stepIndex < steps.length - 1) setStepIndex((index) => index + 1)
        else onComplete()
        return
      }

      target.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      const rect = target.getBoundingClientRect()
      const nextRect = { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
      setTargetRect(nextRect)
      setTooltipPosition(positionTooltip(nextRect))
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [currentStep, onComplete, open, stepIndex, steps.length])

  useEffect(() => {
    if (open) nextButtonRef.current?.focus()
  }, [open, stepIndex])

  if (!open || !currentStep) return null

  const position = tooltipPosition ?? { top: 96, left: 16 }
  const lastStep = stepIndex === steps.length - 1

  function goNext() {
    if (lastStep) onComplete()
    else setStepIndex((index) => index + 1)
  }

  return (
    <>
      <div className="fixed inset-0 z-50 bg-slate-950/45" aria-hidden="true" />
      {targetRect && (
        <div
          className="pointer-events-none fixed z-[51] rounded-lg ring-2 ring-primary ring-offset-2 ring-offset-transparent"
          aria-hidden="true"
          style={{ top: targetRect.top - 4, left: targetRect.left - 4, width: targetRect.width + 8, height: targetRect.height + 8 }}
        />
      )}
      <section
        className="fixed z-[52] w-[min(20rem,calc(100vw-2rem))] rounded-xl border bg-background p-4 text-foreground shadow-2xl"
        style={{ top: position.top, left: position.left }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="guided-tour-title"
        aria-describedby="guided-tour-content"
        data-testid="guided-tour"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Step {stepIndex + 1} of {steps.length}
            </p>
            <h2 id="guided-tour-title" className="mt-1 font-heading text-lg font-bold text-primary">
              {currentStep.title}
            </h2>
          </div>
          <Button type="button" variant="ghost" size="icon" onClick={onComplete} aria-label="Close guided tour">
            <X aria-hidden />
          </Button>
        </div>
        <p id="guided-tour-content" className="mt-3 text-sm leading-6">
          {currentStep.content}
        </p>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
          <Button type="button" variant="ghost" className="px-2" onClick={onComplete}>
            Skip tour
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => setStepIndex((index) => index - 1)} disabled={stepIndex === 0}>
              Back
            </Button>
            <Button ref={nextButtonRef} type="button" onClick={goNext}>
              {lastStep ? 'Finish' : 'Next'}
            </Button>
          </div>
        </div>
      </section>
    </>
  )
}
