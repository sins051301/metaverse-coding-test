import { useEffect, useState } from 'react'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import {
  loungeStore,
  useLoungeState,
} from '@/entities/lounge/model/loungeStore'
import { useReplaced } from '@/entities/lounge/model/rosterStore'
import { readStorage, writeStorage } from '@/shared/lib/storage'
import { Button } from '@/shared/ui/Button'
import { ActivityStrip } from './ActivityStrip'
import { LoungeChat } from './LoungeChat'
import { LoungeMap } from './LoungeMap'
import { NeighborList } from './NeighborList'
import { RecentSolves } from './RecentSolves'
import * as styles from './LoungeMapArea.css'

const COLLAPSED_KEY = 'codingtest:v1:mapCollapsed'

type LoungeMapAreaProps = {
  onStand: () => void
}

export function LoungeMapArea({ onStand }: LoungeMapAreaProps) {
  const { view } = useLoungeState()
  const replaced = useReplaced()
  const [collapsed, setCollapsed] = useState(
    () => readStorage(localStorage, COLLAPSED_KEY) === 'true',
  )
  const panel = view === 'panel'
  const hidden = panel && collapsed

  useEffect(() => {
    loungeStore.setPaused(hidden)
  }, [hidden])

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
      {hidden ? <ActivityStrip onExpand={toggle} /> : null}
      <div className={styles.mapBox} hidden={hidden}>
        <LoungeMap />
        {replaced ? (
          <div className={styles.replaced} role="status">
            <span>다른 탭에서 접속 중이에요</span>
            <Button size="small" onClick={() => loungeConnection.restart()}>
              여기서 접속
            </Button>
          </div>
        ) : null}
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
      {!panel ? <RecentSolves /> : null}
      {!panel ? <LoungeChat variant="overlay" /> : null}
      {panel && !collapsed ? <PanelSide /> : null}
    </aside>
  )
}

function PanelSide() {
  const [tab, setTab] = useState<'neighbors' | 'chat'>('neighbors')
  return (
    <div className={styles.side}>
      <div className={styles.tabs} role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'neighbors'}
          className={styles.tab}
          onClick={() => setTab('neighbors')}
        >
          옆자리
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'chat'}
          className={styles.tab}
          onClick={() => setTab('chat')}
        >
          채팅
        </button>
      </div>
      {tab === 'neighbors' ? <NeighborList /> : <LoungeChat variant="panel" />}
    </div>
  )
}
