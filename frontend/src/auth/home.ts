import { getStoredRole } from './activeRole'
import { ROLE_HOME, activeRole } from './roles'
import { getUser } from './session'

// Role home of the logged-in User's Active role. Reads the session directly (like the guards do),
// so it is correct even before React has re-rendered. Only call it when the User is logged in.
export function currentHome() {
  const user = getUser()
  return ROLE_HOME[activeRole(user?.roles ?? [], getStoredRole())]
}
