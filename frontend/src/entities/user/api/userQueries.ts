import { queryOptions } from '@tanstack/react-query'
import { isHttpError, request } from '@/shared/api/httpClient'

export type CurrentUser = {
  id: number
  nickname: string
  email: string
  totalSolved: number
}

export const CURRENT_USER_QUERY_KEY = ['users', 'me']

// HttpOnly 쿠키라 프론트는 토큰이 있는지 직접 볼 수 없다. 내 정보 조회가 401이면 게스트(null)로 본다
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
