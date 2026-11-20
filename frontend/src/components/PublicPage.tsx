import type { ReactNode } from 'react'

// Page shell of the public pages (/ and /login): a narrow centred column, no header or nav.
export function PublicPage({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-xl flex-col gap-6 px-4 py-8 sm:py-14">
      {children}
    </main>
  )
}
