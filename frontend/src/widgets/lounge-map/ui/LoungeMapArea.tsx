import { useEffect, useState } from 'react'
import {
  loungeStore,
  useLoungeState,
} from '@/entities/lounge/model/loungeStore'
import { readStorage, writeStorage } from '@/shared/lib/storage'
import { Button } from '@/shared/ui/Button'
import { LoungeMap } from './LoungeMap'
import styles from './LoungeMapArea.module.css'

const COLLAPSED_KEY = 'codingtest:v1:mapCollapsed'

type LoungeMapAreaProps = {
  onStand: () => void
}

/**
 * 지도가 놓이는 자리. 화면에 따라 모양만 바뀌고 안의 LoungeMap 은 그대로 남는다.
 *  - 라운지(/): 가운데 전체를 차지
 *  - 책상에 앉은 화면: 왼쪽 280px 패널 + [일어나기], 접으면 48px 띠
 *
 * 주의: 아래 JSX 에서 LoungeMap 상자는 늘 세 번째 자식이어야 한다. 앞 자식을 `조건 ? <요소> : null` 로 두면
 * 자리(null)가 남아 순서가 유지되지만, 자식 수가 바뀌면 React 가 LoungeMap 을 새로 만들어 게임이 다시 시작된다.
 */
export function LoungeMapArea({ onStand }: LoungeMapAreaProps) {
  const { view } = useLoungeState()
  const [collapsed, setCollapsed] = useState(
    () => readStorage(localStorage, COLLAPSED_KEY) === 'true',
  )
  const panel = view === 'panel'
  const hidden = panel && collapsed

  useEffect(() => {
    loungeStore.setPaused(hidden)
  }, [hidden])

  // ESC 로 일어나기. 에디터·입력칸에 있을 때는 그쪽이 ESC 를 먼저 쓰므로(자동 완성 닫기 등) 넘긴다
  useEffect(() => {
    if (!panel) {
      return
    }
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target
      const typing =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        (target instanceof HTMLElement && target.isContentEditable)
      if (event.key === 'Escape' && !typing) {
        onStand()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [panel, onStand])

  function toggle() {
    const next = !collapsed
    setCollapsed(next)
    writeStorage(localStorage, COLLAPSED_KEY, String(next))
  }

  const areaClass = !panel
    ? styles.lounge
    : collapsed
      ? `${styles.panel} ${styles.collapsed}`
      : styles.panel

  return (
    <aside className={areaClass} aria-label="라운지 지도">
      {panel && !collapsed ? (
        <div className={styles.head}>
          <span className={styles.name}>공용 라운지</span>
          <span className={styles.spacer} />
          <Button size="small" variant="quiet" onClick={onStand}>
            일어나기 <kbd className={styles.key}>Esc</kbd>
          </Button>
          <button
            type="button"
            className={styles.iconButton}
            onClick={toggle}
            aria-label="지도 접기"
            title="지도 접기"
          >
            <svg
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M10 3 5 8l5 5" />
            </svg>
          </button>
        </div>
      ) : null}
      {hidden ? (
        <button
          type="button"
          className={styles.iconButton}
          onClick={toggle}
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
      ) : null}
      <div className={styles.mapBox} hidden={hidden}>
        <LoungeMap />
      </div>
      {!panel ? (
        <div className={styles.keys}>
          <span>
            <kbd className={styles.key}>WASD</kbd>이동
          </span>
          <span>
            <kbd className={styles.key}>F</kbd>책상에 앉기
          </span>
        </div>
      ) : null}
    </aside>
  )
}
