import { formatValue } from '@/entities/judge/lib/formatValue'
import type {
  RunResult,
  SubmissionResult,
  TestResult,
  Verdict,
} from '@/entities/judge/model/types'
import type { JudgeView } from '@/features/judge-code/model/useJudge'
import { Button } from '@/shared/ui/Button'
import * as styles from './JudgeResultPanel.css'

const FAILURE_REASON: Record<Verdict, string> = {
  AC: '',
  WA: '',
  TLE: ' (시간 초과)',
  MLE: ' (메모리 초과)',
  RE: ' (런타임 에러)',
  CE: ' (컴파일 에러)',
}

const MILLISECONDS_PER_SECOND = 1000
const KILOBYTES_PER_MEGABYTE = 1024

type JudgeResultPanelProps = {
  view: JudgeView
  timeLimitMs: number
  onRetry: () => void
  onLogin: () => void
}

export function JudgeResultPanel({
  view,
  timeLimitMs,
  onRetry,
  onLogin,
}: JudgeResultPanelProps) {
  const title =
    view.phase !== 'idle' && view.mode === 'submit' ? '채점 결과' : '실행 결과'

  return (
    <section className={styles.panel} aria-labelledby="judge-result-title">
      <h3 id="judge-result-title" className={styles.title}>
        {title}
      </h3>
      <div className={styles.body} aria-live="polite">
        <ResultBody
          view={view}
          timeLimitMs={timeLimitMs}
          onRetry={onRetry}
          onLogin={onLogin}
        />
      </div>
    </section>
  )
}

function ResultBody({
  view,
  timeLimitMs,
  onRetry,
  onLogin,
}: JudgeResultPanelProps) {
  switch (view.phase) {
    case 'idle':
      return (
        <p className={styles.muted}>
          코드 실행을 누르면 예제 테스트 결과가, 제출 후 채점하기를 누르면
          점수가 여기에 나와요.
        </p>
      )
    case 'judging':
      return (
        <div className={styles.skeleton} aria-label="채점 중">
          <span />
          <span />
          <span />
        </div>
      )
    case 'login-required':
      return (
        <div className={styles.notice}>
          <strong>코드 실행과 제출은 로그인하고 쓸 수 있어요.</strong>
          <p>쓰던 코드는 이 브라우저에 그대로 남아 있어요.</p>
          <Button size="small" variant="primary" onClick={onLogin}>
            Google로 로그인
          </Button>
        </div>
      )
    case 'busy':
      return (
        <RetryNotice
          headline="지금 채점을 기다리는 코드가 많아요."
          onRetry={onRetry}
        />
      )
    case 'already-judging':
      return (
        <RetryNotice
          headline="이미 채점 중인 코드가 있어요."
          guide="쓰던 코드는 그대로 있어요. 앞의 채점이 끝난 뒤에 다시 실행해 주세요."
          onRetry={onRetry}
        />
      )
    case 'error':
      return (
        <RetryNotice
          headline="채점 서버에 연결하지 못했어요."
          onRetry={onRetry}
        />
      )
    case 'done':
      if (view.result.verdict === 'CE')
        return <CompileError compileLog={view.result.compileLog} />
      return view.mode === 'run' ? (
        <RunCases result={view.result} timeLimitMs={timeLimitMs} />
      ) : (
        <GradeList result={view.result} />
      )
  }
}

function RetryNotice({
  headline,
  guide = '쓰던 코드는 그대로 있어요. 잠시 뒤에 다시 실행해 주세요.',
  onRetry,
}: {
  headline: string
  guide?: string
  onRetry: () => void
}) {
  return (
    <div className={styles.notice}>
      <strong className={styles.bad}>{headline}</strong>
      <p>{guide}</p>
      <Button size="small" onClick={onRetry}>
        다시 실행
      </Button>
    </div>
  )
}

function CompileError({ compileLog }: { compileLog: string | null }) {
  return (
    <>
      <p className={styles.bad}>
        컴파일 에러가 나서 테스트를 실행하지 않았어요.
      </p>
      <pre className={styles.log}>{compileLog}</pre>
    </>
  )
}

function RunCases({
  result,
  timeLimitMs,
}: {
  result: RunResult
  timeLimitMs: number
}) {
  return result.tests.map((test) => (
    <dl key={test.testNumber} className={styles.runCase}>
      <span className={styles.caseName}>테스트 {test.testNumber}</span>
      <dt>입력값</dt>
      <dd>{formatInput(test.input)}</dd>
      <dt>기댓값</dt>
      <dd>{formatValue(test.expected)}</dd>
      <dt>실행 결과</dt>
      <dd className={test.verdict === 'AC' ? styles.ok : styles.bad}>
        {runOutcome(test, timeLimitMs)}
      </dd>
      {test.userOutput ? (
        <>
          <dt>출력</dt>
          <dd className={styles.output}>{test.userOutput}</dd>
        </>
      ) : null}
      {test.errorMessage ? (
        <>
          <dt>에러</dt>
          <dd className={styles.output}>{test.errorMessage}</dd>
        </>
      ) : null}
    </dl>
  ))
}

function GradeList({ result }: { result: SubmissionResult }) {
  const passedCount = result.tests.filter(
    (test) => test.verdict === 'AC',
  ).length

  return (
    <>
      <div className={styles.scoreRow}>
        <span className={styles.score}>{(result.score ?? 0).toFixed(1)}</span>
        <span className={styles.scoreOf}>/ 100.0</span>
        <span className={styles.muted}>
          {result.tests.length}개 중 {passedCount}개 통과
        </span>
      </div>
      <ul className={styles.gradeList}>
        {result.tests.map((test) => (
          <li key={test.testNumber}>
            <span className={styles.testName}>테스트 {test.testNumber}</span>
            <span className={test.verdict === 'AC' ? styles.ok : styles.bad}>
              {test.verdict === 'AC'
                ? '통과'
                : `실패${FAILURE_REASON[test.verdict]}`}
            </span>
            <span className={styles.cost}>{formatCost(test)}</span>
          </li>
        ))}
      </ul>
    </>
  )
}

function formatInput(input: unknown[] | null) {
  if (!input) return ''
  return input.map(formatValue).join(', ')
}

function formatCost({ timeMs, memoryKb }: TestResult) {
  const seconds = (timeMs / MILLISECONDS_PER_SECOND).toFixed(2)
  const megabytes = (memoryKb / KILOBYTES_PER_MEGABYTE).toFixed(1)
  return `${seconds}초, ${megabytes}MB`
}

function runOutcome(test: TestResult, timeLimitMs: number) {
  switch (test.verdict) {
    case 'AC':
      return '테스트를 통과했어요.'
    case 'WA':
      return `실행한 결괏값 ${formatValue(test.actual)}이(가) 기댓값 ${formatValue(test.expected)}과(와) 달라요.`
    case 'TLE':
      return `시간 제한 ${timeLimitMs / MILLISECONDS_PER_SECOND}초를 넘겼어요.`
    case 'MLE':
      return '메모리 제한을 넘겼어요.'
    case 'RE':
      return '실행 중에 에러가 났어요.'
    case 'CE':
      return '컴파일 에러가 났어요.'
  }
}
