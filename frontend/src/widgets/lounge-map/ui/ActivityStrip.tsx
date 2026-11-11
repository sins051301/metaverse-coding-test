import {
  useOnlineCount,
  useRecentSolves,
  useSeatedNeighbors,
} from '@/entities/lounge/model/rosterStore'
import { useNow } from '@/shared/lib/useNow'
import * as styles from './ActivityStrip.css'

const RECENT_STAR_MS = 5 * 60_000

type ActivityStripProps = {
  onExpand: () => void
}

export function ActivityStrip({ onExpand }: ActivityStripProps) {
  const onlineCount = useOnlineCount()
  const typingCount = useSeatedNeighbors().filter(
    (neighbor) => neighbor.activity === 'typing',
  ).length
  const latest = useRecentSolves()[0]
  const now = useNow()
  const recentlySolved =
    latest !== undefined && now - Date.parse(latest.solvedAt) < RECENT_STAR_MS

  return (
    <div className={styles.strip}>
      <button
        type="button"
        className={styles.iconButton}
        onClick={onExpand}
        aria-label="지도 펼치기"
        title="지도 펼치기"
      >
        <svg
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <path d="m6 3 5 5-5 5" />
        </svg>
      </button>
      {onlineCount !== null ? (
        <div className={styles.figure}>
          <span className={styles.number}>{onlineCount}</span>
          <span className={styles.label}>접속</span>
        </div>
      ) : null}
      <div className={styles.figure}>
        <span className={styles.number}>{typingCount}</span>
        <span className={styles.label}>작성 중</span>
      </div>
      {recentlySolved ? (
        <div
          className={styles.star}
          title={`${latest.nickname}님이 방금 풀었어요`}
          aria-label={`${latest.nickname}님이 방금 풀었어요`}
        >
          <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="M8 1.5 9.9 5.6l4.4.5-3.3 3 .9 4.4L8 11.3l-3.9 2.2.9-4.4-3.3-3 4.4-.5z" />
          </svg>
        </div>
      ) : null}
    </div>
  )
}
