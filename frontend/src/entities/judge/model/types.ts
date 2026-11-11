import type { Language } from '@/shared/config/languages'

export type Verdict = 'AC' | 'WA' | 'TLE' | 'MLE' | 'RE' | 'CE'

export type JudgeStatus = 'PENDING' | 'JUDGING' | 'DONE' | 'FAILED'

export type TestResult = {
  testNumber: number
  hidden: boolean
  verdict: Verdict
  timeMs: number
  memoryKb: number
  input: unknown[] | null
  expected: unknown
  actual: unknown
  userOutput: string | null
  errorMessage: string | null
}

type JudgeResultBase = {
  id: number
  problemId: number
  language: Language
  status: JudgeStatus
  verdict: Verdict | null
  compileLog: string | null
  tests: TestResult[]
}

export type RunResult = JudgeResultBase & {
  passedCount: number
  totalCount: number
}

export type SubmissionResult = JudgeResultBase & {
  sourceCode: string
  score: number | null
}

export type JudgeAccepted = {
  id: number
  status: JudgeStatus
}
