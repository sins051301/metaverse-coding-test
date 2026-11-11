import { useRecentSolves } from '@/entities/lounge/model/rosterStore'
import { useNow } from '@/shared/lib/useNow'
import { formatRelativeTime } from '@/widgets/lounge-map/lib/relativeTime'
import styles from './LoungeOverlays.module.css'

const VISIBLE_COUNT = 3

export function RecentSolves() {
  const recentSolves = useRecentSolves()
  const now = useNow()
  if (recentSolves.length === 0) {
    return null
  }
  return (
    <section
      className={styles.recentSolves}
      aria-labelledby="recent-solves-title"
    >
      <h2 id="recent-solves-title" className={styles.title}>
        방금 푼 고양이
      </h2>
      <ol className={styles.list}>
        {recentSolves.slice(0, VISIBLE_COUNT).map((solve) => (
          <li
            key={`${solve.solvedAt}-${solve.nickname}`}
            className={styles.solve}
          >
            <span className={styles.who}>{solve.nickname}</span>
            <span className={styles.what}>{solve.problemTitle}</span>
            <span className={styles.when}>
              {formatRelativeTime(solve.solvedAt, now)}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}
