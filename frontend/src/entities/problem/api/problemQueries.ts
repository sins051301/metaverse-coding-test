import { queryOptions } from '@tanstack/react-query'
import { request } from '@/shared/api/httpClient'
import type { Language } from '@/shared/config/languages'

export type ProblemSummary = {
  id: number
  title: string
  level: number
}

export type ProblemParameter = {
  name: string
  type: string
}

export type ProblemDetail = ProblemSummary & {
  description: string
  parameters: ProblemParameter[]
  returnType: string
  timeLimitMs: number
  memoryLimitMb: number
  starterCode: Record<Language, string>
}

export function problemListQuery() {
  return queryOptions({
    queryKey: ['problems'],
    queryFn: () => request<ProblemSummary[]>('/api/problems'),
    staleTime: Infinity,
  })
}

export function problemDetailQuery(problemId: number) {
  return queryOptions({
    queryKey: ['problems', problemId],
    queryFn: () => request<ProblemDetail>(`/api/problems/${problemId}`),
    staleTime: Infinity,
  })
}
