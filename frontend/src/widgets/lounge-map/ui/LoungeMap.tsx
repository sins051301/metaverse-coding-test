import type Phaser from 'phaser'
import { useEffect, useRef } from 'react'
import { readSavedAppearance } from '@/entities/avatar/model/appearance'
import {
  loungeStore,
  useLoungeState,
} from '@/entities/lounge/model/loungeStore'
import styles from './LoungeMap.module.css'

/**
 * Phaser 캔버스를 담는 상자와, 그 위에 React 로 띄우는 [F] 툴팁.
 * 이 컴포넌트는 공통 레이아웃(LoungeLayout)에 한 번만 놓여 화면이 바뀌어도 사라지지 않는다. 그래서 게임도 한 번만 만든다.
 */
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
        // 지도를 접으면 그리기를 멈춘다 (에디터와 같이 켜 둘 때 CPU·GPU 를 아끼려고)
        unsubscribe = loungeStore.subscribe(() => {
          if (loungeStore.getState().paused) {
            created.loop.sleep()
          } else {
            created.loop.wake()
          }
        })
      },
    )

    // StrictMode(개발 모드)는 효과를 한 번 지웠다가 다시 실행한다. 지울 때 게임을 정리해야 캔버스가 두 개 생기지 않는다
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
