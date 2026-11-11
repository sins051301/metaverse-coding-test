import { BACKEND_ORIGIN } from '@/shared/config/backend'
import { request } from '@/shared/api/httpClient'
import { readStorage, removeStorage, writeStorage } from '@/shared/lib/storage'

const RETURN_PATH_KEY = 'codingtest:v1:loginReturnPath'

export function startLogin() {
  writeStorage(sessionStorage, RETURN_PATH_KEY, window.location.pathname)
  window.location.assign(`${BACKEND_ORIGIN}/oauth2/authorization/google`)
}

export function takeLoginReturnPath() {
  const returnPath = readStorage(sessionStorage, RETURN_PATH_KEY)
  removeStorage(sessionStorage, RETURN_PATH_KEY)
  return returnPath
}

export function logout() {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
