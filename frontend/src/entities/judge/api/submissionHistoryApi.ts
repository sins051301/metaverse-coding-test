import { infiniteQueryOptions } from '@tanstack/react-query'
import type { JudgeStatus, Verdict } from '@/entities/judge/model/types'
import { request } from '@/shared/api/httpClient'
import type { Language } from '@/shared/config/languages'

export type SubmissionSummary = {
  id: number
  language: Language
  status: JudgeStatus
  verdict: Verdict | null
  score: number | null
  createdAt: string
  judgedAt: string | null
}

export type SubmissionHistoryPage = {
  submissions: SubmissionSummary[]
  totalCount: number | null
  hasNext: boolean
}

export function submissionHistoryQuery(problemId: number) {
  return infiniteQueryOptions({
    queryKey: ['submissions', 'history', problemId],
    queryFn: ({ pageParam }) =>
      request<SubmissionHistoryPage>(
        `/api/problems/${problemId}/submissions?page=${pageParam}`,
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage, pages) =>
      lastPage.hasNext ? pages.length : undefined,
  })
}
