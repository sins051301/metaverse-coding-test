import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { lazy, Suspense, useState } from 'react'
import { submissionResultQuery } from '@/entities/judge/api/judgeApi'
import {
  submissionHistoryQuery,
  type SubmissionSummary,
} from '@/entities/judge/api/submissionHistoryApi'
import { LANGUAGES, type Language } from '@/shared/config/languages'
import { formatDateTime } from '@/shared/lib/formatDateTime'
import { Button } from '@/shared/ui/Button'
import styles from './SubmissionHistory.module.css'

// 코드 보기는 Monaco를 쓰므로 에디터와 같은 덩어리를 행을 펼칠 때 받는다
const CodeViewer = lazy(() =>
  import('@/shared/ui/CodeViewer').then((module) => ({
    default: module.CodeViewer,
  })),
)

const COPIED_MESSAGE_MS = 1500

export function SubmissionHistoryTable({ problemId }: { problemId: number }) {
  const history = useInfiniteQuery(submissionHistoryQuery(problemId))
  const [expandedId, setExpandedId] = useState<number | null>(null)

  if (history.isError)
    return <p className={styles.notice}>제출 내역을 불러오지 못했어요.</p>
  if (!history.data)
    return <p className={styles.notice}>제출 내역을 불러오는 중이에요.</p>

  const submissions = history.data.pages.flatMap((page) => page.submissions)
  const totalCount = history.data.pages[0].totalCount ?? submissions.length

  return (
    <div className={styles.history}>
      <div className={styles.summaryBar}>
        <span className={styles.muted}>{totalCount}개의 제출</span>
        <Button
          size="small"
          variant="quiet"
          onClick={() => void history.refetch()}
          disabled={history.isFetching}
        >
          새로고침
        </Button>
      </div>

      <div className={styles.headerRow} role="row">
        <span />
        <span>제출일시</span>
        <span>언어</span>
        <span>채점 내역</span>
      </div>

      {submissions.length === 0 ? (
        <p className={styles.notice}>아직 이 문제에 제출한 코드가 없어요.</p>
      ) : null}

      <ul className={styles.rows}>
        {submissions.map((submission) => {
          const expanded = expandedId === submission.id
          return (
            <li key={submission.id}>
              <button
                type="button"
                className={styles.row}
                aria-expanded={expanded}
                onClick={() => setExpandedId(expanded ? null : submission.id)}
              >
                <span className={styles.caret} aria-hidden="true">
                  {expanded ? '▾' : '▸'}
                </span>
                <span className={expanded ? styles.strong : ''}>
                  {formatDateTime(submission.createdAt)}
                </span>
                <span className={expanded ? styles.strong : ''}>
                  {LANGUAGES[submission.language].label}
                </span>
                <GradeCell submission={submission} />
              </button>
              {expanded ? (
                <SubmittedCode
                  submissionId={submission.id}
                  language={submission.language}
                />
              ) : null}
            </li>
          )
        })}
      </ul>

      {history.hasNextPage ? (
        <div className={styles.more}>
          <Button
            size="small"
            onClick={() => void history.fetchNextPage()}
            disabled={history.isFetchingNextPage}
          >
            더 보기
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function GradeCell({ submission }: { submission: SubmissionSummary }) {
  if (submission.status === 'PENDING' || submission.status === 'JUDGING') {
    return <span className={styles.muted}>채점 중</span>
  }
  if (submission.status === 'FAILED') {
    return <span className={styles.muted}>채점 실패</span>
  }
  const accepted = submission.verdict === 'AC'
  return (
    <span className={styles.grade}>
      <span className={accepted ? styles.ok : styles.bad}>
        <svg
          viewBox="0 0 16 16"
          className={styles.verdictIcon}
          aria-hidden="true"
        >
          <circle cx="8" cy="8" r="8" fill="currentColor" />
          <path
            d={
              accepted
                ? 'm4.5 8.2 2.3 2.3 4.7-4.8'
                : 'm5.3 5.3 5.4 5.4m0-5.4-5.4 5.4'
            }
            fill="none"
            stroke="var(--color-card)"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {accepted ? '정답' : '오답'}
      </span>
      <span className={styles.muted}>{submission.score ?? 0} / 100</span>
    </span>
  )
}

function SubmittedCode({
  submissionId,
  language,
}: {
  submissionId: number
  language: Language
}) {
  const { data: submission, isError } = useQuery(
    submissionResultQuery(submissionId),
  )
  const [copied, setCopied] = useState(false)

  if (isError) return <p className={styles.notice}>코드를 불러오지 못했어요.</p>
  if (!submission)
    return <p className={styles.notice}>코드를 불러오는 중이에요.</p>

  async function copy(code: string) {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), COPIED_MESSAGE_MS)
  }

  return (
    <div className={styles.codePanel}>
      <div className={styles.copyBar}>
        <Button
          size="small"
          variant="primary"
          onClick={() => void copy(submission.sourceCode)}
        >
          {copied ? '복사했어요' : '복사'}
        </Button>
      </div>
      <div className={styles.code}>
        <Suspense
          fallback={<p className={styles.notice}>코드를 불러오는 중이에요.</p>}
        >
          <CodeViewer
            code={submission.sourceCode}
            language={LANGUAGES[language].monacoLanguage}
          />
        </Suspense>
      </div>
    </div>
  )
}
