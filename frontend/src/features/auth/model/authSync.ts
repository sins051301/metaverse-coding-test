import type { QueryClient } from '@tanstack/react-query'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import { PROBLEM_LIST_QUERY_KEY } from '@/entities/problem/api/problemQueries'
import { CURRENT_USER_QUERY_KEY } from '@/entities/user/api/userQueries'
import { isHttpError } from '@/shared/api/httpClient'

const channel = new BroadcastChannel('codingtest:v1:auth')

export function refreshAuthState(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY })
  void queryClient.invalidateQueries({
    queryKey: PROBLEM_LIST_QUERY_KEY,
    exact: true,
  })
  loungeConnection.restart()
}

export function announceAuthChange() {
  channel.postMessage('changed')
}

export function listenAuthChangeElsewhere(queryClient: QueryClient) {
  const onMessage = () => refreshAuthState(queryClient)
  channel.addEventListener('message', onMessage)
  return () => channel.removeEventListener('message', onMessage)
}

export function handleUnauthorized(queryClient: QueryClient, error: unknown) {
  const signedIn = Boolean(queryClient.getQueryData(CURRENT_USER_QUERY_KEY))
  if (!isHttpError(error, 401) || !signedIn) return
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null)
  refreshAuthState(queryClient)
  announceAuthChange()
}
