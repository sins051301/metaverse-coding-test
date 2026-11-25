import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import {
  isFinished,
  requestJudge,
  runResultQuery,
  submissionResultQuery,
  type JudgeMode,
  type SourceCode,
} from '@/entities/judge/api/judgeApi'
import type { RunResult, SubmissionResult } from '@/entities/judge/model/types'
import { loungeStore } from '@/entities/lounge/model/loungeStore'
import { PROBLEM_LIST_QUERY_KEY } from '@/entities/problem/api/problemQueries'
import { CURRENT_USER_QUERY_KEY } from '@/entities/user/api/userQueries'
import { isHttpError } from '@/shared/api/httpClient'
import { celebrate } from '@/shared/lib/celebrate'

export type JudgeView =
  | { phase: 'idle' }
  | { phase: 'judging'; mode: JudgeMode }
  | { phase: 'login-required'; mode: JudgeMode }
  | { phase: 'busy'; mode: JudgeMode }
  | { phase: 'already-judging'; mode: JudgeMode }
  | { phase: 'error'; mode: JudgeMode }
  | { phase: 'done'; mode: 'run'; result: RunResult }
  | { phase: 'done'; mode: 'submit'; result: SubmissionResult }

export function useJudge(problemId: number) {
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<JudgeMode>('run')
  const [jobId, setJobId] = useState<number | null>(null)

  const accept = useMutation({
    mutationFn: ({ mode, source }: { mode: JudgeMode; source: SourceCode }) =>
      requestJudge(mode, problemId, source),
    onSuccess: (accepted) => setJobId(accepted.id),
  })
  const run = useQuery({
    ...runResultQuery(jobId ?? 0),
    enabled: mode === 'run' && jobId !== null,
  })
  const submission = useQuery({
    ...submissionResultQuery(jobId ?? 0),
    enabled: mode === 'submit' && jobId !== null,
  })

  const submittedVerdict =
    mode === 'submit' && submission.data?.status === 'DONE'
      ? submission.data.verdict
      : null
  useEffect(() => {
    if (submittedVerdict === 'AC') {
      celebrate()
      void queryClient.invalidateQueries({
        queryKey: PROBLEM_LIST_QUERY_KEY,
        exact: true,
      })
      void queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY })
    } else if (submittedVerdict !== null) {
      loungeStore.flashFailure()
    }
  }, [jobId, submittedVerdict, queryClient])

  function judge(nextMode: JudgeMode, source: SourceCode) {
    setMode(nextMode)
    setJobId(null)
    accept.mutate({ mode: nextMode, source })
  }

  function view(): JudgeView {
    if (accept.isPending) return { phase: 'judging', mode }
    if (accept.isError) {
      if (isHttpError(accept.error, 401))
        return { phase: 'login-required', mode }
      if (isHttpError(accept.error, 409))
        return { phase: 'already-judging', mode }
      if (isHttpError(accept.error, 429)) return { phase: 'busy', mode }
      return { phase: 'error', mode }
    }
    if (jobId === null) return { phase: 'idle' }

    const polling = mode === 'run' ? run : submission
    if (polling.isError)
      return isHttpError(polling.error, 401)
        ? { phase: 'login-required', mode }
        : { phase: 'error', mode }
    if (!polling.data || !isFinished(polling.data.status))
      return { phase: 'judging', mode }
    if (polling.data.status === 'FAILED') return { phase: 'error', mode }
    if (mode === 'run' && run.data)
      return { phase: 'done', mode: 'run', result: run.data }
    if (mode === 'submit' && submission.data)
      return { phase: 'done', mode: 'submit', result: submission.data }
    return { phase: 'judging', mode }
  }

  const current = view()
  return { view: current, isJudging: current.phase === 'judging', judge }
}
