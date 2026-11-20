import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { refresh } from './auth/refresh'
import { routeTree } from './routeTree.gen'
import './index.css'

const queryClient = new QueryClient()
// The real auth value is supplied by <App/> below; this placeholder is never read.
const router = createRouter({ routeTree, context: { auth: undefined! } })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

// Gives the router the current auth so `beforeLoad` guards can use it.
function App() {
  const auth = useAuth()
  return <RouterProvider router={router} context={{ auth }} />
}

// Restore the session from the refresh cookie before the first render, so the route guards do not
// send a logged-in user to /login on reload. A 401 just means "logged out". The 1 s retry only
// happens when a cookie was refused (SESSION_EXPIRED), so visitors without a cookie are not delayed.
void refresh().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  )
})
