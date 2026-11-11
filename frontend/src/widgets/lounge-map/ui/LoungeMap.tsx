import type Phaser from 'phaser'
import { useEffect, useRef } from 'react'
import { readSavedAppearance } from '@/entities/avatar/model/appearance'
import {
  loungeStore,
  useLoungeState,
} from '@/entities/lounge/model/loungeStore'
import * as styles from './LoungeMap.css'

export function LoungeMap() {
  const containerRef = useRef<HTMLDivElement>(null)
  const { nearbyDesk } = useLoungeState()

  useEffect(() => {
    let game: Phaser.Game | null = null
    let cancelled = false
    let unsubscribe = () => {}

    void import('@/widgets/lounge-map/lib/startLoungeGame').then(
      ({ startLoungeGame }) => {
        if (cancelled || !containerRef.current) {
          return
        }
        const created = startLoungeGame(
          containerRef.current,
          readSavedAppearance(),
        )
        game = created
        unsubscribe = loungeStore.subscribe(() => {
          if (loungeStore.getState().paused) {
            created.loop.sleep()
          } else {
            created.loop.wake()
          }
        })
      },
    )

    return () => {
      cancelled = true
      unsubscribe()
      game?.destroy(true)
    }
  }, [])

  return (
    <div className={styles.map}>
      <div ref={containerRef} className={styles.canvasHost} />
      {nearbyDesk ? (
        <div
          className={styles.tooltip}
          style={{ left: nearbyDesk.screenX, top: nearbyDesk.screenY }}
        >
          <kbd className={styles.key}>F</kbd>책상에 앉기
        </div>
      ) : null}
    </div>
  )
}
