import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import type { Role } from '@/auth/types'
import { getHelpDocument, parseHelpMarkdown } from '@/help'
import { Button } from '@/components/ui/button'

interface HelpDrawerProps {
  open: boolean
  pathname: string
  role: Role
  onClose: () => void
}

export function HelpDrawer({ open, pathname, role, onClose }: HelpDrawerProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return

    closeButtonRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose, open])

  if (!open) return null

  const helpDocument = getHelpDocument(pathname, role)
  const blocks = parseHelpMarkdown(helpDocument.markdown)

  return (
    <div className="fixed inset-0 z-40" data-testid="help-drawer">
      <button
        type="button"
        className="absolute inset-0 bg-slate-950/40"
        aria-label="Fermer l’aide contextuelle"
        onClick={onClose}
      />
      <aside
        className="absolute inset-x-0 bottom-0 flex max-h-[85vh] flex-col rounded-t-2xl border bg-background shadow-2xl md:inset-y-0 md:right-0 md:left-auto md:w-[min(28rem,100vw)] md:max-h-none md:rounded-none md:border-l md:border-t-0"
        role="dialog"
        aria-modal="true"
        aria-labelledby="contextual-help-title"
      >
        <div className="flex items-start justify-between gap-3 border-b px-4 py-4 sm:px-6">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Aide pour cette page</p>
            <h2 id="contextual-help-title" className="font-heading text-xl font-bold text-primary">
              {helpDocument.title}
            </h2>
          </div>
          <Button ref={closeButtonRef} type="button" variant="outline" size="icon" onClick={onClose} aria-label="Fermer l’aide contextuelle">
            <X aria-hidden />
          </Button>
        </div>

        <div className="overflow-y-auto px-4 py-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] sm:px-6">
          <div className="flex flex-col gap-4 text-sm leading-6">
            {blocks.map((block, index) => {
              if (block.type === 'heading') {
                return (
                  <h3 key={`${block.type}-${index}`} className="font-heading text-base font-semibold text-foreground">
                    {block.text}
                  </h3>
                )
              }
              if (block.type === 'list') {
                return (
                  <ul key={`${block.type}-${index}`} className="list-disc space-y-2 pl-5">
                    {block.items.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                )
              }
              return <p key={`${block.type}-${index}`}>{block.text}</p>
            })}
          </div>
        </div>
      </aside>
    </div>
  )
}
