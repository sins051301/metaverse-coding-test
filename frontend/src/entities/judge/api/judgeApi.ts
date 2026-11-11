import { queryOptions } from '@tanstack/react-query'
import type {
  JudgeAccepted,
  JudgeStatus,
  RunResult,
  SubmissionResult,
} from '@/entities/judge/model/types'
import type { Language } from '@/shared/config/languages'
import { request } from '@/shared/api/httpClient'

export type JudgeMode = 'run' | 'submit'

export type SourceCode = {
  language: Language
  sourceCode: string
}

const POLL_INTERVAL_MS = 1000
const FINISHED_STATUSES: JudgeStatus[] = ['DONE', 'FAILED']

export function isFinished(status: JudgeStatus) {
  return FINISHED_STATUSES.includes(status)
}

export function requestJudge(
  mode: JudgeMode,
  problemId: number,
  body: SourceCode,
) {
  const resource = mode === 'run' ? 'runs' : 'submissions'
  return request<JudgeAccepted>(`/api/problems/${problemId}/${resource}`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function runResultQuery(runId: number) {
  return queryOptions({
    queryKey: ['runs', runId],
    queryFn: () => request<RunResult>(`/api/runs/${runId}`),
    refetchInterval: (query) =>
      query.state.data && isFinished(query.state.data.status)
        ? false
        : POLL_INTERVAL_MS,
  })
}

export function submissionResultQuery(submissionId: number) {
  return queryOptions({
    queryKey: ['submissions', submissionId],
    queryFn: () =>
      request<SubmissionResult>(`/api/submissions/${submissionId}`),
    refetchInterval: (query) =>
      query.state.data && isFinished(query.state.data.status)
        ? false
        : POLL_INTERVAL_MS,
  })
}
