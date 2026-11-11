import { memo } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ProblemDetail } from '@/entities/problem/api/problemQueries'
import styles from './ProblemStatement.module.css'

const REMARK_PLUGINS = [remarkGfm]
const MILLISECONDS_PER_SECOND = 1000

export const ProblemStatement = memo(function ProblemStatement({
  problem,
}: {
  problem: ProblemDetail
}) {
  return (
    <section className={styles.statement} aria-label="문제 설명">
      <div className={styles.limits}>
        <span className={styles.pill}>
          시간 {problem.timeLimitMs / MILLISECONDS_PER_SECOND}초
        </span>
        <span className={styles.pill}>메모리 {problem.memoryLimitMb}MB</span>
      </div>
      <div className={styles.markdown}>
        <Markdown remarkPlugins={REMARK_PLUGINS}>
          {problem.description}
        </Markdown>
      </div>
    </section>
  )
})
