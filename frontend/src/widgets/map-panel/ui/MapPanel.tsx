import { useState } from 'react'
import { readStorage, writeStorage } from '@/shared/lib/storage'
import styles from './MapPanel.module.css'

const COLLAPSED_KEY = 'codingtest:v1:mapCollapsed'

// 라운지 지도(Phaser) 자리. 지금은 접기·펼치기와 접힘 상태 기억만 동작한다
export function MapPanel() {
  const [collapsed, setCollapsed] = useState(
    () => readStorage(localStorage, COLLAPSED_KEY) === 'true',
  )

  function toggle() {
    const next = !collapsed
    setCollapsed(next)
    writeStorage(localStorage, COLLAPSED_KEY, String(next))
  }

  if (collapsed) {
    return (
      <aside
        className={`${styles.panel} ${styles.collapsed}`}
        aria-label="라운지 지도"
      >
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
      </aside>
    )
  }

  return (
    <aside className={styles.panel} aria-label="라운지 지도">
      <div className={styles.head}>
        <span className={styles.name}>공용 라운지</span>
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
      <p className={styles.placeholder}>라운지 지도는 준비 중이에요.</p>
    </aside>
  )
}
