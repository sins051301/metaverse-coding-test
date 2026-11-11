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
  // 첫 페이지에서만 온다. 다음 페이지는 null
  totalCount: number | null
  hasNext: boolean
}

// [더 보기]를 누를 때마다 다음 페이지를 이어 붙인다. 화면을 열 때마다 다시 받아 방금 낸 제출도 보인다
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
