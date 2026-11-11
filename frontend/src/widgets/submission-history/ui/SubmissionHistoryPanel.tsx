import { useQuery } from '@tanstack/react-query'
import { currentUserQuery } from '@/entities/user/api/userQueries'
import { startLogin } from '@/features/auth/model/login'
import { Button } from '@/shared/ui/Button'
import { SubmissionHistoryTable } from './SubmissionHistoryTable'
import * as styles from './SubmissionHistory.css'

export function SubmissionHistoryPanel({ problemId }: { problemId: number }) {
  const { data: user } = useQuery(currentUserQuery())

  return (
    <section className={styles.panel} aria-label="제출 내역">
      {user ? <SubmissionHistoryTable problemId={problemId} /> : null}
      {user === null ? (
        <div className={styles.notice}>
          <p>제출 내역은 로그인하고 볼 수 있어요.</p>
          <Button size="small" variant="primary" onClick={startLogin}>
            Google로 로그인
          </Button>
        </div>
      ) : null}
    </section>
  )
}
