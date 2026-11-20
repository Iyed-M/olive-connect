import type { Me } from './types'

// The session lives in memory only (never localStorage): a page reload logs the user out
// until the refresh cookie is added. A module variable is used so that api.ts and the router
// guards always read the current value, even before React has re-rendered.
let accessToken: string | null = null
let user: Me | null = null

export const getAccessToken = () => accessToken
export const getUser = () => user

export function setSession(token: string | null, me: Me | null) {
  accessToken = token
  user = me
}
