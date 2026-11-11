import { queryOptions } from '@tanstack/react-query'
import { isHttpError, request } from '@/shared/api/httpClient'

export type CurrentUser = {
  id: number
  nickname: string
  email: string
  totalSolved: number
}

export const CURRENT_USER_QUERY_KEY = ['users', 'me']

export function currentUserQuery() {
  return queryOptions({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: async () => {
      try {
        return await request<CurrentUser>('/api/users/me')
      } catch (error) {
        if (isHttpError(error, 401)) return null
        throw error
      }
    },
    staleTime: Infinity,
  })
}
