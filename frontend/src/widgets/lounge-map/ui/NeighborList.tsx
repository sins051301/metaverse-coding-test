import { useLoungeState } from '@/entities/lounge/model/loungeStore'
import {
  type SeatedNeighbor,
  useSeatedNeighbors,
} from '@/entities/lounge/model/rosterStore'
import { useNow } from '@/shared/lib/useNow'
import { DESKS } from '@/widgets/lounge-map/model/loungeMap'
import styles from './LoungeOverlays.module.css'

const RECENTLY_SOLVED_MS = 5 * 60_000

function statusOf(neighbor: SeatedNeighbor, now: number) {
  if (
    neighbor.solvedAt !== null &&
    now - neighbor.solvedAt < RECENTLY_SOLVED_MS
  ) {
    return '방금 풀었음'
  }
  if (neighbor.activity === 'typing') {
    return '작성 중'
  }
  if (neighbor.activity === 'napping') {
    return '낮잠 중'
  }
  return '앉아 있음'
}

function deskDistance(from: number, to: number) {
  return Math.hypot(DESKS[from].x - DESKS[to].x, DESKS[from].y - DESKS[to].y)
}

export function NeighborList() {
  const neighbors = useSeatedNeighbors()
  const { seatedDeskId } = useLoungeState()
  const now = useNow()
  if (neighbors.length === 0) {
    return <p className={styles.empty}>아직 옆자리에 아무도 없어요.</p>
  }
  const sorted =
    seatedDeskId === null
      ? neighbors
      : neighbors.toSorted(
          (left, right) =>
            deskDistance(seatedDeskId, left.deskId!) -
            deskDistance(seatedDeskId, right.deskId!),
        )
  return (
    <section className={styles.neighbors} aria-labelledby="neighbors-title">
      <h3 id="neighbors-title" className={styles.subtitle}>
        옆자리
      </h3>
      <ul className={styles.list}>
        {sorted.map((neighbor) => (
          <li key={neighbor.id} className={styles.neighbor}>
            <span className={styles.who}>{neighbor.nickname}</span>
            <span className={styles.doing}>{statusOf(neighbor, now)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
